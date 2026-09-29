package com.currency.demo.feed;

import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Sends {@link FeedStatus} to Kafka on its own thread, keeping only the newest pending one.
 *
 * <p>{@code send()} can block for up to {@code max.block.ms} when Kafka is down; doing that
 * on the failover-check thread would delay switching sources. A single worker with a
 * one-slot queue and "discard oldest" means a backlog can never build up: an old status
 * is worthless once a newer one exists.
 */
public class FeedStatusPublisher {

    private static final Logger log = LoggerFactory.getLogger(FeedStatusPublisher.class);

    private final KafkaOperations<String, Object> kafka;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofPlatform().name("feed-status").daemon().factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public FeedStatusPublisher(KafkaOperations<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publish(FeedStatus status) {
        executor.execute(() -> {
            try {
                kafka.send(Topics.FEED_STATUS, PriceTick.BTC_USD, status);
            } catch (RuntimeException e) {
                log.warn("Could not publish feed status: {}", e.toString());
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
