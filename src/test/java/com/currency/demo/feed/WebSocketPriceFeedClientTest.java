package com.currency.demo.feed;

import com.currency.demo.feed.PriceFeedClient.BlockMode;
import com.currency.demo.pricing.PriceTick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The real JDK-WebSocket client against a local fake exchange (no internet): reconnects
 * after a server close, aborts half-open connections via the idle watchdog, and honours
 * fault injection. Idle timeout 1 s and backoff 100-400 ms keep the test fast.
 */
class WebSocketPriceFeedClientTest {

    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(1);

    private final List<PriceTick> ticks = new CopyOnWriteArrayList<>();
    private final CoinbaseMessageParser parser = new CoinbaseMessageParser();
    private FakeExchangeServer server;
    private WebSocketPriceFeedClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeExchangeServer(Fixtures.read("coinbase/ticker.json"));
        server.startAndWait();
        server.pumping(true);
        client = new WebSocketPriceFeedClient(server.uri(), parser, (source, tick) -> ticks.add(tick),
                HttpClient.newHttpClient(), Clock.systemUTC(),
                IDLE_TIMEOUT, Duration.ofMillis(100), Duration.ofMillis(400));
    }

    @AfterEach
    void tearDown() throws Exception {
        client.stop();
        server.shutdown();
    }

    @Test
    void subscribesAndDeliversTicks() {
        client.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> !ticks.isEmpty());
        assertThat(server.received()).first().isEqualTo(parser.subscribeMessage());
        assertThat(ticks.getFirst().source()).isEqualTo("coinbase");
        assertThat(client.isConnected()).isTrue();
        assertThat(client.lastTickAt()).isPresent();
    }

    @Test
    void reconnectsAfterServerClosesTheConnection() {
        client.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> !ticks.isEmpty());
        assertThat(server.connectionsOpened()).isEqualTo(1);

        server.closeAllConnections();

        await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionsOpened() >= 2);
        int before = ticks.size();
        await().atMost(Duration.ofSeconds(5)).until(() -> ticks.size() > before);
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void idleWatchdogAbortsHalfOpenConnectionAndReconnects() {
        client.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> !ticks.isEmpty());

        // Connection stays open but nothing arrives any more - like a silently dead network path.
        server.pumping(false);

        await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionsOpened() >= 2);

        server.pumping(true);
        int before = ticks.size();
        await().atMost(Duration.ofSeconds(5)).until(() -> ticks.size() > before);
    }

    @Test
    void blockDisconnectDropsConnectionAndPreventsReconnectUntilUnblocked() throws Exception {
        client.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> !ticks.isEmpty());

        client.block(BlockMode.DISCONNECT);
        await().atMost(Duration.ofSeconds(2)).until(() -> !client.isConnected());
        int connections = server.connectionsOpened();
        int tickCount = ticks.size();

        Thread.sleep(800); // several backoff periods: no reconnect may happen while blocked
        assertThat(server.connectionsOpened()).isEqualTo(connections);
        assertThat(ticks).hasSize(tickCount);
        assertThat(client.blockMode()).isEqualTo(BlockMode.DISCONNECT);

        client.unblock();
        await().atMost(Duration.ofSeconds(5)).until(() -> client.isConnected() && ticks.size() > tickCount);
    }

    @Test
    void blockSilentKeepsSocketButDeliversNothingUntilUnblocked() throws Exception {
        client.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> !ticks.isEmpty());

        client.block(BlockMode.SILENT);
        Thread.sleep(150); // let in-flight messages drain
        int tickCount = ticks.size();

        Thread.sleep(500); // < idle timeout: socket still open, server still pumping, yet no ticks
        assertThat(client.isConnected()).isTrue();
        assertThat(ticks).hasSize(tickCount);

        // After the idle timeout the watchdog treats it as half-open and reconnects (still silent).
        await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionsOpened() >= 2);
        assertThat(ticks).hasSize(tickCount);

        client.unblock();
        await().atMost(Duration.ofSeconds(5)).until(() -> ticks.size() > tickCount);
    }

    // ---- backoff sequence and reset rule (QA CONCERN 11); initial 100 ms, max 400 ms ----

    @Test
    void backoffDoublesUpToTheMaximumWhileTheServerIsDown() throws Exception {
        server.shutdown();
        client.start();

        List<Long> observed = new CopyOnWriteArrayList<>();
        long deadline = System.nanoTime() + Duration.ofMillis(1_500).toNanos();
        while (System.nanoTime() < deadline) {
            long ms = client.nextBackoff().toMillis();
            if (observed.isEmpty() || observed.getLast() != ms) {
                observed.add(ms);
            }
            Thread.sleep(5);
        }

        // 100 -> 200 -> 400, then capped at 400 (it may already be past 100 at the first sample).
        assertThat(observed).isSubsetOf(100L, 200L, 400L).isSorted().contains(400L);
        assertThat(observed.getLast()).isEqualTo(400L);
    }

    @Test
    void backoffKeepsGrowingWhenServerAcceptsButDropsBeforeAnyMessage() throws Exception {
        server.closeOnOpen(true);
        client.start();

        await().atMost(Duration.ofSeconds(5)).until(() -> server.connectionsOpened() >= 3);
        // Connections "succeed" but deliver nothing, so the backoff must NOT reset to 100 ms.
        await().atMost(Duration.ofSeconds(5)).until(() -> client.nextBackoff().toMillis() == 400);
        assertThat(ticks).isEmpty();
    }

    @Test
    void backoffResetsOnceANewConnectionDeliversItsFirstMessage() throws Exception {
        int port = server.getPort();
        server.shutdown();
        client.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> client.nextBackoff().toMillis() == 400);

        server = new FakeExchangeServer(port, Fixtures.read("coinbase/ticker.json"));
        server.startAndWait();
        server.pumping(true);

        await().atMost(Duration.ofSeconds(5)).until(() -> !ticks.isEmpty());
        assertThat(client.nextBackoff()).isEqualTo(Duration.ofMillis(100));
        assertThat(client.lastMessageAt()).isPresent();
    }

    @Test
    void keepsRetryingWhileServerIsDownAndConnectsWhenItComesBack() throws Exception {
        int port = server.getPort();
        server.shutdown();                  // exchange unreachable: every connect fails
        client.start();
        Thread.sleep(600);                  // several failed attempts with backoff
        assertThat(client.isConnected()).isFalse();
        assertThat(ticks).isEmpty();

        // The exchange comes back on the same address; the client must find it on its own.
        server = new FakeExchangeServer(port, Fixtures.read("coinbase/ticker.json"));
        server.startAndWait();
        server.pumping(true);

        await().atMost(Duration.ofSeconds(5)).until(() -> client.isConnected() && !ticks.isEmpty());
    }
}
