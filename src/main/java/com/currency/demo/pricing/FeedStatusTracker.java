package com.currency.demo.pricing;

import com.currency.demo.config.Topics;
import com.currency.demo.feed.FeedStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the latest {@link FeedStatus} seen on {@code btc.feed.status}, on every instance.
 *
 * <p>The API may run on instances without ingest (Kubernetes), so it cannot ask FeedManager.
 * Each instance reads the compacted status topic with its own group id from the earliest
 * offset, which quickly yields the last status after a restart.
 *
 * <p>If no status arrived for {@link #MAX_AGE} the ingest side is presumably gone; we then
 * report DISCONNECTED instead of repeating an old "LIVE" - stale data must never look live.
 */
@Component
public class FeedStatusTracker {

    static final Duration MAX_AGE = Duration.ofSeconds(15);

    private final AtomicReference<FeedStatus> latest = new AtomicReference<>();
    private final Clock clock;

    public FeedStatusTracker(Clock clock) {
        this.clock = clock;
    }

    @KafkaListener(topics = Topics.FEED_STATUS, groupId = "feed-status-${random.uuid}",
            containerFactory = "feedStatusListenerFactory", properties = "auto.offset.reset=earliest")
    public void onStatus(FeedStatus status) {
        if (status != null) {
            latest.accumulateAndGet(status, (old, neu) ->
                    old == null || !neu.reportedAt().isBefore(old.reportedAt()) ? neu : old);
        }
    }

    public Optional<FeedStatus> current() {
        FeedStatus status = latest.get();
        if (status == null) {
            return Optional.empty();
        }
        if (Duration.between(status.reportedAt(), clock.instant()).compareTo(MAX_AGE) > 0) {
            return Optional.of(new FeedStatus(status.activeSource(), FeedStatus.State.DISCONNECTED,
                    status.lastTickAt(), status.reportedAt()));
        }
        return Optional.of(status);
    }
}
