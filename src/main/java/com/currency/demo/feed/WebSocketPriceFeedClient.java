package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A self-healing WebSocket connection to one exchange, built on the JDK's
 * {@link java.net.http.WebSocket} (no extra library).
 *
 * <p>Behaviour:
 * <ul>
 *   <li><b>Reconnect with exponential backoff</b> (initial -> x2 -> max) after any close,
 *       error or failed connect. The backoff resets once a new connection delivers its
 *       first valid message, so a server that accepts and immediately drops us cannot
 *       make us hammer it at the minimum delay.</li>
 *   <li><b>Idle watchdog</b>: a real network outage often never delivers a FIN/RST, and the
 *       socket then looks "open" forever (half-open). Both exchanges send a heartbeat
 *       about once per second, so if nothing at all arrives for {@code idleTimeout} we
 *       {@code abort()} the socket and reconnect.</li>
 *   <li><b>Generations</b>: each connection gets a number; callbacks from an older socket
 *       (e.g. an error arriving after we aborted it) are ignored, so one outage never
 *       triggers two reconnect loops.</li>
 *   <li><b>Fault injection</b> ({@link BlockMode}) for manual acceptance tests.</li>
 * </ul>
 * All scheduling runs on one single-threaded executor per client.
 */
public class WebSocketPriceFeedClient implements PriceFeedClient {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPriceFeedClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final URI url;
    private final FeedMessageParser parser;
    private final TickListener listener;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Duration idleTimeout;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final AtomicInteger generation = new AtomicInteger();

    private volatile ScheduledExecutorService executor;
    private volatile boolean running;
    private volatile WebSocket webSocket;
    private volatile Instant lastMessageAt;   // idle-watchdog timer (also reset on connect)
    private volatile Instant lastReceivedAt;  // last real message, for health checks
    private volatile Instant lastTickAt;
    private volatile boolean receivedSinceConnect;
    private volatile Duration nextBackoff;
    private volatile BlockMode blockMode = BlockMode.NONE;
    private volatile ScheduledFuture<?> pendingReconnect;

    public WebSocketPriceFeedClient(URI url, FeedMessageParser parser, TickListener listener, HttpClient httpClient,
                                    Clock clock, Duration idleTimeout, Duration initialBackoff, Duration maxBackoff) {
        this.url = url;
        this.parser = parser;
        this.listener = listener;
        this.httpClient = httpClient;
        this.clock = clock;
        this.idleTimeout = idleTimeout;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.nextBackoff = initialBackoff;
    }

    @Override
    public String sourceName() {
        return parser.sourceName();
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("feed-" + sourceName()).daemon().factory());
        long checkEveryMillis = Math.max(50, idleTimeout.toMillis() / 4);
        executor.scheduleWithFixedDelay(this::checkIdle, checkEveryMillis, checkEveryMillis, TimeUnit.MILLISECONDS);
        executor.execute(this::connect);
    }

    @Override
    public synchronized void stop() {
        running = false;
        generation.incrementAndGet();
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.abort();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isConnected() {
        return webSocket != null;
    }

    @Override
    public Optional<Instant> lastTickAt() {
        return Optional.ofNullable(lastTickAt);
    }

    @Override
    public Optional<Instant> lastMessageAt() {
        return Optional.ofNullable(lastReceivedAt);
    }

    @Override
    public BlockMode blockMode() {
        return blockMode;
    }

    @Override
    public void block(BlockMode mode) {
        blockMode = mode;
        log.warn("[{}] fault injection: blocked ({})", sourceName(), mode);
        if (mode == BlockMode.DISCONNECT) {
            submit(() -> dropConnection(generation.get(), "blocked"));
        }
    }

    @Override
    public void unblock() {
        blockMode = BlockMode.NONE;
        log.warn("[{}] fault injection: unblocked", sourceName());
        submit(() -> {
            if (webSocket == null) {
                // Skip the remaining backoff: reconnect through the normal path right away.
                ScheduledFuture<?> pending = pendingReconnect;
                if (pending != null) {
                    pending.cancel(false);
                }
                nextBackoff = initialBackoff;
                connect();
            }
        });
    }

    // ---- connection lifecycle (runs on the executor thread) ----

    private void connect() {
        if (!running || webSocket != null) {
            return;
        }
        if (blockMode == BlockMode.DISCONNECT) {
            log.debug("[{}] connect suppressed by fault injection", sourceName());
            scheduleReconnect();
            return;
        }
        int gen = generation.incrementAndGet();
        log.info("[{}] connecting to {}", sourceName(), url);
        httpClient.newWebSocketBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(url, new Listener(gen))
                .whenComplete((ws, error) -> submit(() -> onConnectResult(gen, ws, error)));
    }

    private void onConnectResult(int gen, WebSocket ws, Throwable error) {
        if (gen != generation.get() || !running) {
            if (ws != null) {
                ws.abort();
            }
            return;
        }
        if (error != null) {
            log.warn("[{}] connect failed: {}", sourceName(), rootMessage(error));
            scheduleReconnect();
            return;
        }
        webSocket = ws;
        receivedSinceConnect = false;
        lastMessageAt = clock.instant(); // the idle timer starts now
        log.info("[{}] connected, subscribing", sourceName());
        ws.sendText(parser.subscribeMessage(), true);
    }

    private void dropConnection(int gen, String reason) {
        if (gen != generation.get() || webSocket == null) {
            return;
        }
        WebSocket ws = webSocket;
        webSocket = null;
        generation.incrementAndGet(); // late callbacks from this socket are now ignored
        ws.abort();
        log.warn("[{}] connection dropped ({}), reconnecting in {} ms", sourceName(), reason, nextBackoff.toMillis());
        scheduleReconnect();
    }

    /** The delay before the next reconnect attempt (for tests and diagnostics). */
    Duration nextBackoff() {
        return nextBackoff;
    }

    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        Duration delay = nextBackoff;
        Duration doubled = nextBackoff.multipliedBy(2);
        nextBackoff = doubled.compareTo(maxBackoff) > 0 ? maxBackoff : doubled;
        pendingReconnect = executor.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void checkIdle() {
        Instant last = lastMessageAt;
        if (webSocket != null && last != null
                && Duration.between(last, clock.instant()).compareTo(idleTimeout) >= 0) {
            dropConnection(generation.get(), "no message for " + idleTimeout.toMillis() + " ms (idle watchdog)");
        }
    }

    // ---- incoming messages (runs on the HttpClient's thread) ----

    private void onMessage(int gen, String text) {
        if (gen != generation.get() || blockMode == BlockMode.SILENT) {
            return; // stale socket, or simulating a half-open connection
        }
        Instant now = clock.instant();
        FeedMessage message = parser.parse(text, now);
        switch (message) {
            case FeedMessage.Trades trades -> {
                markAlive(now);
                lastTickAt = now;
                for (PriceTick tick : trades.ticks()) {
                    listener.onTick(sourceName(), tick);
                }
            }
            case FeedMessage.Heartbeat heartbeat -> markAlive(now);
            case FeedMessage.Control control -> markAlive(now);
            case FeedMessage.ExchangeError err -> {
                markAlive(now);
                log.warn("[{}] exchange error: {}", sourceName(), err.message());
            }
            case FeedMessage.Invalid invalid -> log.debug("[{}] ignored message: {}", sourceName(), invalid.reason());
        }
    }

    private void markAlive(Instant now) {
        lastMessageAt = now;
        lastReceivedAt = now;
        if (!receivedSinceConnect) {
            receivedSinceConnect = true;
            nextBackoff = initialBackoff; // healthy again: next outage starts from the initial backoff
        }
    }

    private void submit(Runnable task) {
        ScheduledExecutorService ex = executor;
        if (running && ex != null && !ex.isShutdown()) {
            ex.execute(task);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /** JDK WebSocket callbacks; text may arrive in several parts, delivered one at a time. */
    private final class Listener implements WebSocket.Listener {

        private final int gen;
        private final StringBuilder buffer = new StringBuilder();

        Listener(int gen) {
            this.gen = gen;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                onMessage(gen, text);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            submit(() -> dropConnection(gen, "closed by server: " + statusCode + " " + reason));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            submit(() -> dropConnection(gen, "error: " + rootMessage(error)));
        }
    }
}
