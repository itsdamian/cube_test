package com.currency.demo.feed;

import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hands price ticks to Kafka without ever blocking the caller.
 *
 * <p>The caller is a WebSocket listener thread. If it called {@code KafkaTemplate.send()}
 * directly and Kafka were down, {@code send()} could block for {@code max.block.ms}, freezing
 * the feed - and the failover logic would then wrongly think the exchange went quiet.
 * So {@link #publish} only puts the tick into a bounded in-memory queue, and one dedicated
 * "tick-publisher" thread drains the queue into Kafka.
 *
 * <p>If Kafka stays down long enough for the queue to fill, the <em>oldest</em> tick is
 * dropped (the newest price is the most useful one) and the {@code feed.ticks.dropped}
 * counter goes up. Back-filling is a non-goal of the spec. When Kafka recovers the
 * publisher simply continues.
 */
public class TickPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TickPublisher.class);
    private static final long LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final KafkaOperations<String, Object> kafka;
    private final BlockingQueue<PriceTick> queue;
    private final Counter dropped;
    private final AtomicLong lastDropLogNanos = new AtomicLong(System.nanoTime() - LOG_INTERVAL_NANOS);
    private final AtomicLong lastSendErrorLogNanos = new AtomicLong(System.nanoTime() - LOG_INTERVAL_NANOS);

    private volatile boolean running;
    private volatile Thread worker;

    public TickPublisher(KafkaOperations<String, Object> kafka, int capacity, MeterRegistry registry) {
        this.kafka = kafka;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.dropped = Counter.builder("feed.ticks.dropped")
                .description("Ticks discarded because the publish queue was full (Kafka unavailable)")
                .register(registry);
    }

    /** Enqueue a tick for Kafka. Never blocks; drops the oldest queued tick when full. */
    public void publish(PriceTick tick) {
        while (!queue.offer(tick)) {
            if (queue.poll() != null) {
                dropped.increment();
                logThrottled(lastDropLogNanos,
                        "Tick publish queue full (Kafka unavailable?) - dropping oldest ticks, total dropped={}",
                        (long) dropped.count(), null);
            }
        }
    }

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("tick-publisher").daemon().start(this::drainLoop);
    }

    @Override
    public void stop() {
        running = false;
        Thread t = worker;
        if (t != null) {
            t.interrupt();
            try {
                t.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void drainLoop() {
        while (running) {
            try {
                PriceTick tick = queue.poll(500, TimeUnit.MILLISECONDS);
                if (tick != null) {
                    send(tick);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void send(PriceTick tick) {
        try {
            // May block up to max.block.ms (5s) if the broker is unreachable - fine, this is our own thread.
            kafka.send(Topics.PRICE_TICKS, tick.pair(), tick).whenComplete((result, ex) -> {
                if (ex != null) {
                    logThrottled(lastSendErrorLogNanos, "Failed to deliver tick to Kafka: {}", null, ex);
                }
            });
        } catch (RuntimeException ex) {
            logThrottled(lastSendErrorLogNanos, "Failed to send tick to Kafka: {}", null, ex);
        }
    }

    /** At most one warning per 10 seconds per kind, so an outage does not flood the log. */
    private static void logThrottled(AtomicLong last, String message, Long value, Throwable ex) {
        long now = System.nanoTime();
        long prev = last.get();
        if (now - prev >= LOG_INTERVAL_NANOS && last.compareAndSet(prev, now)) {
            log.warn(message, value != null ? value : (ex != null ? ex.toString() : ""));
        }
    }

    /** Snapshot of ticks still waiting to be sent (oldest first). For tests and diagnostics. */
    List<PriceTick> pending() {
        return new ArrayList<>(queue);
    }

    double droppedCount() {
        return dropped.count();
    }
}
