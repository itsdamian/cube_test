package com.currency.demo.feed;

import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test against a real broker (Testcontainers): topics are created at startup
 * and a tick published through {@link TickPublisher} comes back unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PriceTickKafkaRoundTripTest extends IntegrationTest {

    @Autowired
    TickPublisher publisher;

    // Use Spring-managed Kafka beans: @ServiceConnection applies the container's address to
    // them, whereas the raw KafkaProperties still hold application.yml's localhost:9092.
    @Autowired
    KafkaAdmin kafkaAdmin;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void topicsAreCreatedWithExpectedSettings() throws Exception {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Map<String, TopicDescription> topics = admin.describeTopics(
                    List.of(Topics.PRICE_TICKS, Topics.CANDLES, Topics.ALERTS_TRIGGERED, Topics.FEED_STATUS))
                    .allTopicNames().get();
            assertThat(topics.get(Topics.PRICE_TICKS).partitions()).hasSize(3);

            ConfigResource status = new ConfigResource(ConfigResource.Type.TOPIC, Topics.FEED_STATUS);
            Config config = admin.describeConfigs(Set.of(status)).all().get().get(status);
            assertThat(config.get("cleanup.policy").value()).isEqualTo("compact");
        }
    }

    @Test
    void publishedTickRoundTripsThroughKafkaWithoutLosingPrecision() {
        PriceTick sent = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD,
                new BigDecimal("67123.45000000"), "coinbase",
                Instant.parse("2026-09-29T07:00:00.123456Z"), Instant.parse("2026-09-29T07:00:00.200000001Z"));

        publisher.publish(sent);

        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        AtomicReference<ConsumerRecord<String, String>> received = new AtomicReference<>();

        // Read the raw JSON text, so we can check both the wire format and the decoded object.
        try (Consumer<String, String> consumer = consumerFactory.createConsumer(
                "roundtrip-" + UUID.randomUUID(), null, null, overrides)) {
            consumer.subscribe(List.of(Topics.PRICE_TICKS));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(200))) {
                    if (r.value().contains(sent.eventId().toString())) {
                        received.set(r);
                    }
                }
                return received.get() != null;
            });
        }

        ConsumerRecord<String, String> record = received.get();
        assertThat(record.key()).isEqualTo("BTC-USD");
        assertThat(record.headers().lastHeader("__TypeId__")).as("no Java type headers").isNull();
        assertThat(record.value())
                .contains("\"price\":67123.45000000")                         // exact digits, not a double
                .contains("\"eventTime\":\"2026-09-29T07:00:00.123456Z\"");  // ISO-8601, not epoch number

        try (JsonDeserializer<PriceTick> json = new JsonDeserializer<>(PriceTick.class, objectMapper, false)) {
            PriceTick decoded = json.deserialize(Topics.PRICE_TICKS, record.value().getBytes(StandardCharsets.UTF_8));
            assertThat(decoded).isEqualTo(sent); // record equals: same BigDecimal scale and nanosecond Instants
        }
    }
}
