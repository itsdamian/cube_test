package com.currency.demo.pricing;

import com.currency.demo.feed.FeedStatus;
import com.currency.demo.feed.FeedStatus.State;
import com.currency.demo.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FeedStatusTrackerTest {

    private static final Instant T0 = Instant.parse("2026-09-29T11:00:00Z");
    private final MutableClock clock = new MutableClock(T0);
    private final FeedStatusTracker tracker = new FeedStatusTracker(clock);

    @Test
    void emptyUntilAStatusArrives() {
        assertThat(tracker.current()).isEmpty();
    }

    @Test
    void freshStatusIsReportedAsIs() {
        tracker.onStatus(new FeedStatus("coinbase", State.LIVE, T0, T0));
        clock.advance(Duration.ofSeconds(15));

        assertThat(tracker.current()).get().extracting(FeedStatus::state).isEqualTo(State.LIVE);
    }

    @Test
    void statusOlderThan15SecondsIsReportedAsDisconnected() {
        tracker.onStatus(new FeedStatus("coinbase", State.LIVE, T0, T0));
        clock.advance(Duration.ofSeconds(15).plusMillis(1));

        FeedStatus current = tracker.current().orElseThrow();
        assertThat(current.state()).isEqualTo(State.DISCONNECTED);
        assertThat(current.activeSource()).isEqualTo("coinbase");
    }

    @Test
    void anOlderStatusArrivingLateDoesNotOverwriteANewerOne() {
        tracker.onStatus(new FeedStatus("kraken", State.LIVE, T0, T0.plusSeconds(5)));
        tracker.onStatus(new FeedStatus("coinbase", State.LIVE, T0, T0));

        assertThat(tracker.current()).get().extracting(FeedStatus::activeSource).isEqualTo("kraken");
    }
}
