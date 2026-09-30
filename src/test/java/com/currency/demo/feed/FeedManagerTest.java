package com.currency.demo.feed;

import com.currency.demo.feed.FeedStatus.State;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Failover rules, driven by a mutable clock and direct {@code check()} calls (no sleeping).
 * Defaults as in production: stale after 10 s, switch back after 15 s of healthy primary.
 */
class FeedManagerTest {

    private static final Instant T0 = Instant.parse("2026-09-29T08:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final TickPublisher tickPublisher = mock(TickPublisher.class);
    private final FeedStatusPublisher statusPublisher = mock(FeedStatusPublisher.class);
    private FakeFeedClient coinbase;
    private FakeFeedClient kraken;
    private FeedManager manager;

    @BeforeEach
    void setUp() {
        manager = new FeedManager((parser, listener) -> {
            FakeFeedClient fake = new FakeFeedClient(parser.sourceName(), listener, clock);
            if (parser.sourceName().equals("coinbase")) {
                coinbase = fake;
            } else {
                kraken = fake;
            }
            return fake;
        }, new CoinbaseMessageParser(), new KrakenMessageParser(), tickPublisher, statusPublisher, clock,
                Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(15));
    }

    /** Advance one second at a time; the given sources tick each second; check() after each step. */
    private void run(int seconds, FakeFeedClient... ticking) {
        for (int i = 0; i < seconds; i++) {
            clock.advance(Duration.ofSeconds(1));
            for (FakeFeedClient c : ticking) {
                c.tick();
            }
            manager.check();
        }
    }

    @Test
    void startsOnPrimaryAndIsLiveOnceItTicks() {
        manager.check();
        assertThat(manager.currentStatus().state()).isEqualTo(State.STALE); // connected, no price yet

        run(1, coinbase, kraken);

        assertThat(manager.currentStatus().activeSource()).isEqualTo("coinbase");
        assertThat(manager.currentStatus().state()).isEqualTo(State.LIVE);
    }

    @Test
    void exactlyTheStaleThresholdCountsAsStale() {
        coinbase.tick();
        kraken.tick();
        manager.check();

        clock.set(T0.plusMillis(9_999));
        kraken.tick();
        manager.check();
        assertThat(manager.activeSource()).as("9.999 s: not stale yet").isEqualTo("coinbase");

        clock.set(T0.plusSeconds(10));
        kraken.tick();
        manager.check();
        assertThat(manager.activeSource()).as("exactly 10 s: stale").isEqualTo("kraken");
    }

    @Test
    void switchesToBackupWithin11SecondsOfThePrimarysLastTickAndPublishesImmediately() {
        run(3, coinbase, kraken);
        // Last primary tick at an awkward offset relative to the 1-second check cadence.
        clock.advance(Duration.ofMillis(300));
        coinbase.tick();
        Instant lastPrimaryTick = clock.instant();
        kraken.tick();
        manager.check();
        clearInvocations(statusPublisher);

        Instant switchedAt = null;
        for (int i = 0; i < 30 && switchedAt == null; i++) {
            clock.advance(Duration.ofSeconds(1));
            kraken.tick();
            manager.check();
            if (manager.activeSource().equals("kraken")) {
                switchedAt = clock.instant();
            }
        }

        assertThat(switchedAt).isNotNull();
        assertThat(Duration.between(lastPrimaryTick, switchedAt)).isLessThanOrEqualTo(Duration.ofSeconds(11));
        ArgumentCaptor<FeedStatus> published = ArgumentCaptor.forClass(FeedStatus.class);
        verify(statusPublisher, atLeastOnce()).publish(published.capture());
        FeedStatus atSwitch = published.getAllValues().stream()
                .filter(s -> s.activeSource().equals("kraken")).findFirst().orElseThrow();
        assertThat(atSwitch.reportedAt()).as("published in the same check that switched").isEqualTo(switchedAt);
        assertThat(atSwitch.state()).isEqualTo(State.LIVE);
    }

    @Test
    void switchesBackOnlyAfterPrimaryIsHealthyForTheFullRecoveryPeriod() {
        run(2, coinbase, kraken);
        run(11, kraken);                              // primary silent -> backup
        assertThat(manager.activeSource()).isEqualTo("kraken");

        run(1, coinbase, kraken);                      // primary healthy again from `back`
        Instant back = clock.instant();

        run(14, coinbase, kraken);
        assertThat(Duration.between(back, clock.instant())).isEqualTo(Duration.ofSeconds(14));
        assertThat(manager.activeSource()).isEqualTo("kraken");

        run(1, coinbase, kraken);                      // exactly 15 s -> switch back
        assertThat(manager.activeSource()).isEqualTo("coinbase");
        assertThat(manager.currentStatus().state()).isEqualTo(State.LIVE);
    }

    @Test
    void primaryGoingStaleDuringRecoveryRestartsTheTimer() {
        run(2, coinbase, kraken);
        run(11, kraken);
        assertThat(manager.activeSource()).isEqualTo("kraken");

        run(8, coinbase, kraken);                      // recovering...
        coinbase.connected(false);                     // ...then drops
        run(1, kraken);
        coinbase.connected(true);

        run(1, coinbase, kraken);                      // healthy again: the timer restarts here
        Instant back = clock.instant();
        run(14, coinbase, kraken);                     // back + 14 s (22 s since first recovery!)
        assertThat(manager.activeSource()).isEqualTo("kraken");
        assertThat(Duration.between(back, clock.instant())).isEqualTo(Duration.ofSeconds(14));
        run(1, coinbase, kraken);                      // 15 s of uninterrupted health
        assertThat(manager.activeSource()).isEqualTo("coinbase");
    }

    @Test
    void bothDownIsDisconnectedAndBothSilentButConnectedIsStale() {
        run(2, coinbase, kraken);

        run(10);                                       // nobody ticks, sockets open
        assertThat(manager.currentStatus().state()).isEqualTo(State.STALE);

        coinbase.connected(false);
        kraken.connected(false);
        run(1);
        assertThat(manager.currentStatus().state()).isEqualTo(State.DISCONNECTED);
    }

    @Test
    void whenBothWereDownTheFirstToRecoverWinsThenPrimaryReturnsAfterRecoveryPeriod() {
        run(2, coinbase, kraken);
        coinbase.connected(false);
        kraken.connected(false);
        run(12);
        assertThat(manager.currentStatus().state()).isEqualTo(State.DISCONNECTED);

        kraken.connected(true);
        run(1, kraken);                                // backup recovers first
        assertThat(manager.activeSource()).isEqualTo("kraken");
        assertThat(manager.currentStatus().state()).isEqualTo(State.LIVE);

        coinbase.connected(true);
        run(1, coinbase, kraken);
        Instant back = clock.instant();
        run(14, coinbase, kraken);
        assertThat(Duration.between(back, clock.instant())).isEqualTo(Duration.ofSeconds(14));
        assertThat(manager.activeSource()).isEqualTo("kraken");
        run(1, coinbase, kraken);
        assertThat(manager.activeSource()).isEqualTo("coinbase");
    }

    // ---- health = any message within 10 s AND a trade within 60 s (QA CONCERN 12) ----

    /** Advance one second at a time; `trading` sources trade, `quiet` sources only send a heartbeat. */
    private void runMixed(int seconds, List<FakeFeedClient> trading, List<FakeFeedClient> quiet) {
        for (int i = 0; i < seconds; i++) {
            clock.advance(Duration.ofSeconds(1));
            trading.forEach(FakeFeedClient::tick);
            quiet.forEach(FakeFeedClient::heartbeat);
            manager.check();
        }
    }

    @Test
    void heartbeatsKeepAQuietSourceHealthyForUpToSixtySecondsWithoutTrades() {
        run(2, coinbase, kraken);
        runMixed(30, List.of(kraken), List.of(coinbase));  // coinbase: no trades for 30 s, heartbeats only

        assertThat(manager.activeSource()).as("no false failover in a quiet market").isEqualTo("coinbase");
        assertThat(manager.currentStatus().state()).isEqualTo(State.LIVE);
    }

    @Test
    void lastMessageBoundaryIsTenSecondsEvenWithRecentTrades() {
        coinbase.tick();
        kraken.tick();
        manager.check();

        clock.set(T0.plusMillis(9_999));
        kraken.tick();
        manager.check();
        assertThat(manager.activeSource()).as("9.999 s since any coinbase message").isEqualTo("coinbase");

        clock.set(T0.plusSeconds(10));
        kraken.tick();
        manager.check();
        assertThat(manager.activeSource()).as("10 s without even a heartbeat").isEqualTo("kraken");
    }

    @Test
    void priceStaleBoundaryIsSixtySecondsWhileHeartbeatsContinue() {
        coinbase.tick();                                   // last coinbase trade at T0
        kraken.tick();
        manager.check();
        for (int s = 1; s <= 59; s++) {                    // heartbeats keep the socket "alive"
            clock.set(T0.plusSeconds(s));
            coinbase.heartbeat();
            kraken.tick();
            manager.check();
        }
        clock.set(T0.plusMillis(59_999));
        coinbase.heartbeat();
        kraken.tick();
        manager.check();
        assertThat(manager.activeSource()).as("59.999 s without a trade").isEqualTo("coinbase");

        clock.set(T0.plusSeconds(60));
        coinbase.heartbeat();
        kraken.tick();
        manager.check();
        assertThat(manager.activeSource()).as("60 s without a trade").isEqualTo("kraken");
    }

    @Test
    void primaryWithOnlyHeartbeatsForOverSixtySecondsFailsOverToTradingBackup() {
        run(2, coinbase, kraken);
        runMixed(61, List.of(kraken), List.of(coinbase));

        assertThat(manager.activeSource()).isEqualTo("kraken");
        assertThat(manager.currentStatus().state()).isEqualTo(State.LIVE);
    }

    @Test
    void bothWithOnlyHeartbeatsForOverSixtySecondsIsStaleNotLive() {
        run(2, coinbase, kraken);
        runMixed(30, List.of(), List.of(coinbase, kraken));
        assertThat(manager.currentStatus().state()).as("quiet but within 60 s").isEqualTo(State.LIVE);

        runMixed(31, List.of(), List.of(coinbase, kraken));

        FeedStatus status = manager.currentStatus();
        assertThat(status.state()).as("connections alive, prices too old -> 資料延遲").isEqualTo(State.STALE);
        assertThat(status.lastTickAt()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void onlyTheActiveSourcesTicksArePublished() {
        run(1, coinbase, kraken);
        clearInvocations(tickPublisher);

        PriceTick fromPrimary = coinbase.tick();
        PriceTick fromBackup = kraken.tick();

        verify(tickPublisher).publish(fromPrimary);
        verify(tickPublisher, never()).publish(fromBackup);

        run(11, kraken);                               // now on backup
        clearInvocations(tickPublisher);
        PriceTick primaryAgain = coinbase.tick();
        PriceTick backupNow = kraken.tick();
        verify(tickPublisher, never()).publish(primaryAgain);
        verify(tickPublisher).publish(backupNow);
    }

    @Test
    void statusIsRepublishedEvery5SecondsEvenWithoutChanges() {
        run(1, coinbase, kraken);
        clearInvocations(statusPublisher);

        run(10, coinbase, kraken);                     // only lastTickAt changes -> just the 5 s heartbeat
        verify(statusPublisher, times(2)).publish(org.mockito.ArgumentMatchers.any());

        // Frozen clock: nothing changes, so only the 5-second heartbeat publishes.
        clearInvocations(statusPublisher);
        coinbase.connected(false);
        kraken.connected(false);
        manager.check();                                           // change -> publish
        for (int i = 0; i < 9; i++) {
            clock.advance(Duration.ofSeconds(1));
            manager.check();
        }
        ArgumentCaptor<FeedStatus> captor = ArgumentCaptor.forClass(FeedStatus.class);
        verify(statusPublisher, times(2)).publish(captor.capture());   // at the change, then 5 s later
        List<FeedStatus> values = captor.getAllValues();
        assertThat(Duration.between(values.get(0).reportedAt(), values.get(1).reportedAt()))
                .isEqualTo(Duration.ofSeconds(5));
    }
}
