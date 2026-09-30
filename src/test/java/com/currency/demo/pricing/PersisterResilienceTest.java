package com.currency.demo.pricing;

import com.currency.demo.config.Topics;
import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The tick persister must not get stuck on garbage: an unreadable record is skipped and later
 * ticks are still stored. (Retry-until-the-database-is-back is covered by
 * {@link com.currency.demo.config.PersistingErrorHandlerTest}.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PersisterResilienceTest extends IntegrationTest {

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    ProducerFactory<String, Object> producerFactory;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PriceTickRepository ticks;

    @Test
    void unreadableRecordIsSkippedAndLaterTicksAreStillStored() {
        String source = "pp-" + UUID.randomUUID().toString().substring(0, 8);
        // Raw bytes that are not a PriceTick, sent with a plain byte-array producer.
        try (var raw = new org.apache.kafka.clients.producer.KafkaProducer<>(
                Map.copyOf(producerFactory.getConfigurationProperties()),
                new org.apache.kafka.common.serialization.StringSerializer(),
                new org.apache.kafka.common.serialization.ByteArraySerializer())) {
            raw.send(new org.apache.kafka.clients.producer.ProducerRecord<>(Topics.PRICE_TICKS, PriceTick.BTC_USD,
                    "{ this is not a tick".getBytes(StandardCharsets.UTF_8)));
        }
        send(source, Instant.parse("2033-02-01T00:00:00Z"));
        kafka.flush();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(count(source)).isEqualTo(1));
    }

    @Test
    void aTickTheDatabaseRejectsIsDroppedButTheRestOfItsBatchIsStored() {
        String source = "ov-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t = Instant.parse("2033-03-01T00:00:00Z");
        // 16 integer digits do not fit numeric(20,8) -> "numeric field overflow" for that row only.
        PriceTick tooBig = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("1000000000000000"),
                source, t, t);
        kafka.send(Topics.PRICE_TICKS, tooBig.pair(), tooBig);
        send(source, t.plusSeconds(1));
        send(source, t.plusSeconds(2));
        kafka.flush();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(count(source)).isEqualTo(2));
    }

    @Test
    void repositoryIsolatesTheRejectedRowWithinOneBatch() {
        String source = "ob-" + UUID.randomUUID().toString().substring(0, 8);
        Instant t = Instant.parse("2033-04-01T00:00:00Z");
        ticks.insertAll(java.util.List.of(
                new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("84000"), source, t, t),
                new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("1000000000000000"), source, t, t),
                new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("84001"), source, t, t)));

        assertThat(count(source)).isEqualTo(2);
    }

    private void send(String source, Instant t) {
        PriceTick tick = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal("84000.00"), source, t, t);
        kafka.send(Topics.PRICE_TICKS, tick.pair(), tick);
    }

    private int count(String source) {
        return jdbc.queryForObject("SELECT count(*) FROM price_tick WHERE source = ?", Integer.class, source);
    }
}
