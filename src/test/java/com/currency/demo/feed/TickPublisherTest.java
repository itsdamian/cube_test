package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (no broker): the publisher must never block the WebSocket thread, even when
 * Kafka hangs, and must drop the OLDEST ticks when its queue is full.
 */
class TickPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, Object> kafka = mock(KafkaOperations.class);
    private final CountDownLatch kafkaHangs = new CountDownLatch(1);
    private TickPublisher publisher;

    @AfterEach
    void tearDown() {
        kafkaHangs.countDown();
        if (publisher != null) {
            publisher.stop();
        }
    }

    @Test
    void publishNeverBlocksWhenKafkaHangsAndDropsOldestWhenFull() {
        // Simulate an unreachable broker: send() blocks like it would for max.block.ms.
        when(kafka.send(anyString(), anyString(), any())).thenAnswer(inv -> {
            kafkaHangs.await(30, TimeUnit.SECONDS);
            return new CompletableFuture<SendResult<String, Object>>();
        });
        publisher = new TickPublisher(kafka, 3, new SimpleMeterRegistry());
        publisher.start();

        PriceTick first = tick(0);
        publisher.publish(first);
        // The worker takes the first tick and gets stuck inside send().
        verify(kafka, timeout(2_000)).send(eq("btc.price.ticks"), eq("BTC-USD"), eq(first));

        PriceTick[] later = new PriceTick[8];
        for (int i = 0; i < later.length; i++) {
            later[i] = tick(i + 1);
            long start = System.nanoTime();
            publisher.publish(later[i]);
            long tookMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(tookMillis).as("publish() #%d must not block", i).isLessThan(10);
        }

        // Capacity 3: of the 8 queued ticks the 5 oldest were dropped, the 3 newest remain.
        assertThat(publisher.droppedCount()).isEqualTo(5.0);
        assertThat(publisher.pending()).containsExactly(later[5], later[6], later[7]);
    }

    @Test
    void sendsQueuedTicksInOrderOnceKafkaIsAvailable() {
        when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        publisher = new TickPublisher(kafka, 100, new SimpleMeterRegistry());

        PriceTick a = tick(1);
        PriceTick b = tick(2);
        publisher.publish(a);
        publisher.publish(b);
        publisher.start();

        verify(kafka, timeout(2_000)).send("btc.price.ticks", "BTC-USD", a);
        verify(kafka, timeout(2_000)).send("btc.price.ticks", "BTC-USD", b);
        await().atMost(Duration.ofSeconds(2)).until(() -> publisher.pending().isEmpty());
        assertThat(publisher.droppedCount()).isZero();
    }

    @Test
    void aFailingSendDoesNotStopThePublisher() {
        when(kafka.send(anyString(), anyString(), any()))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("metadata not available"))
                .thenReturn(new CompletableFuture<>());
        publisher = new TickPublisher(kafka, 10, new SimpleMeterRegistry());
        publisher.start();

        publisher.publish(tick(1));
        PriceTick second = tick(2);
        publisher.publish(second);

        verify(kafka, timeout(2_000)).send("btc.price.ticks", "BTC-USD", second);
        assertThat(publisher.isRunning()).isTrue();
    }

    private static PriceTick tick(int n) {
        Instant t = Instant.parse("2026-09-29T07:00:00Z").plusSeconds(n);
        return new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("67000.00").add(BigDecimal.valueOf(n)),
                "coinbase", t, t);
    }
}
