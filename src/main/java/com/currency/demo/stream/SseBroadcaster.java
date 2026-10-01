package com.currency.demo.stream;

import com.currency.demo.alert.AlertDtos.AlertTriggered;
import com.currency.demo.config.Topics;
import com.currency.demo.feed.FeedStatus;
import com.currency.demo.pricing.FeedStatusTracker;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.stream.StreamEvents.Price;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pushes Kafka events to every browser connected to {@code /api/stream} on THIS instance.
 *
 * <ul>
 *   <li>Each instance reads the topics with its <b>own consumer group</b> (random id) from the
 *       <b>latest</b> offset, so with several replicas every instance sees every event and
 *       serves its own connections; nobody replays history.</li>
 *   <li><b>Price throttling</b>: ticks can arrive dozens per second. Only the newest is kept and
 *       pushed at most every {@value #PRICE_INTERVAL_MS} ms, and only if it changed, so the
 *       last price of a burst is always delivered.</li>
 *   <li>Status and alert events are pushed immediately; a comment line every 15 s keeps
 *       proxies from closing idle connections.</li>
 *   <li>All writes go through one thread, so events on a connection never interleave.
 *       A failed write means the browser left: the emitter is removed.</li>
 *   <li><b>The periodic tasks never die</b> (task 32): {@link ScheduledExecutorService} silently
 *       cancels every later run of a periodic task once a run throws, without logging. That once
 *       froze the price for every browser while status events (sent via {@code execute}) kept
 *       saying LIVE. So a failing emitter is dropped individually, and each periodic run is
 *       guarded and logged.</li>
 *   <li><b>Metrics</b>: {@code cube_sse_last_push_seconds} is when the push loop last took a NEW
 *       price, whether or not any browser is connected - so it only goes stale when the loop
 *       itself freezes (the task 32 incident), never just because nobody has the page open.
 *       {@code cube_sse_prices_pushed_total} counts price events actually written to browsers,
 *       {@code cube_sse_connections} the open streams.</li>
 * </ul>
 */
@Component
public class SseBroadcaster implements DisposableBean {

    static final long PRICE_INTERVAL_MS = 250;
    static final long HEARTBEAT_SECONDS = 15;
    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicReference<PriceTick> latestTick = new AtomicReference<>();
    private final AtomicReference<PriceTick> lastPushed = new AtomicReference<>();
    private final FeedStatusTracker statusTracker;
    private final Clock clock;
    private final Counter pricesPushed;
    private volatile Instant lastPushAt;
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("sse-sender").daemon().factory());

    public SseBroadcaster(FeedStatusTracker statusTracker, MeterRegistry registry, Clock clock) {
        this.statusTracker = statusTracker;
        this.clock = clock;
        this.pricesPushed = Counter.builder("cube.sse.prices.pushed")
                .description("Price events written to connected browsers")
                .register(registry);
        Gauge.builder("cube.sse.last.push.seconds", this,
                        b -> b.lastPushAt == null ? Double.NaN : b.lastPushAt.toEpochMilli() / 1000.0)
                .description("Epoch seconds when the push loop last took a new price (independent of connected clients)")
                .register(registry);
        Gauge.builder("cube.sse.connections", emitters, Set::size)
                .description("Open SSE connections on this instance")
                .register(registry);
        sender.scheduleAtFixedRate(guarded("price push", this::pushLatestPrice),
                PRICE_INTERVAL_MS, PRICE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        sender.scheduleAtFixedRate(guarded("heartbeat", this::heartbeat),
                HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** A periodic task that logs and carries on instead of being cancelled by one failed run. */
    static Runnable guarded(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("SSE {} failed; retrying on the next run", name, e);
            }
        };
    }

    /** Register a new browser connection and immediately send what we know. */
    public SseEmitter connect() {
        return register(new SseEmitter(0L)); // no server-side timeout; the heartbeat keeps it alive
    }

    /** Package-private so tests can register emitters that fail in specific ways. */
    SseEmitter register(SseEmitter emitter) {
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        sender.execute(() -> {
            statusTracker.current().ifPresent(status -> send(emitter, StreamEvents.STATUS, status));
            PriceTick tick = lastPushed.get() != null ? lastPushed.get() : latestTick.get();
            if (tick != null) {
                send(emitter, StreamEvents.PRICE, Price.of(tick));
            }
        });
        return emitter;
    }

    public int connectionCount() {
        return emitters.size();
    }

    @KafkaListener(topics = Topics.PRICE_TICKS, groupId = "sse-ticks-${random.uuid}",
            containerFactory = "tickLatestListenerFactory", properties = "auto.offset.reset=latest")
    public void onTicks(List<PriceTick> ticks) {
        ticks.stream().filter(Objects::nonNull).reduce((a, b) -> b).ifPresent(latestTick::set);
    }

    @KafkaListener(topics = Topics.FEED_STATUS, groupId = "sse-status-${random.uuid}",
            containerFactory = "feedStatusListenerFactory", properties = "auto.offset.reset=latest")
    public void onStatus(FeedStatus status) {
        if (status != null) {
            sender.execute(() -> broadcast(StreamEvents.STATUS, status));
        }
    }

    @KafkaListener(topics = Topics.ALERTS_TRIGGERED, groupId = "sse-alerts-${random.uuid}",
            containerFactory = "alertTriggeredListenerFactory", properties = "auto.offset.reset=latest")
    public void onAlert(AlertTriggered alert) {
        if (alert != null) {
            sender.execute(() -> broadcast(StreamEvents.ALERT, alert));
        }
    }

    private void pushLatestPrice() {
        PriceTick tick = latestTick.get();
        if (tick != null && tick != lastPushed.get()) {
            lastPushed.set(tick);
            lastPushAt = clock.instant();
            broadcast(StreamEvents.PRICE, Price.of(tick));
        }
    }

    private void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | RuntimeException e) {
                emitters.remove(emitter);
            }
        }
    }

    private void broadcast(String name, Object data) {
        for (SseEmitter emitter : emitters) {
            send(emitter, name, data);
        }
    }

    /** Send to one connection; any failure drops just that connection, never the others. */
    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            if (StreamEvents.PRICE.equals(name)) {
                pricesPushed.increment();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("SSE client gone: {}", e.toString());
            emitters.remove(emitter);
            try {
                emitter.completeWithError(e);
            } catch (RuntimeException alreadyGone) {
                // the connection is already completed / its request no longer usable: nothing to close
                log.debug("SSE emitter already closed: {}", alreadyGone.toString());
            }
        }
    }

    @Override
    public void destroy() {
        sender.shutdownNow();
        emitters.forEach(SseEmitter::complete);
    }
}
