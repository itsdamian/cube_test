package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.alert.AlertDtos.AlertEvent;
import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.support.IntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alerts are evaluated on the backend from Kafka ticks, with no browser connected (AC7, AC8).
 * The threshold (9,000,000) is far above every other test's prices, so only our ticks fire it.
 * Cooldown is 5 minutes of EVENT time, so the test sends ticks with chosen event times.
 */
@SpringBootTest(properties = "app.alerts.enabled=true")
@AutoConfigureMockMvc
class AlertEvaluationTest extends IntegrationTest {

    static final Instant T0 = Instant.parse("2034-06-01T10:00:00Z");

    @Autowired
    AlertRepository repository;

    @Autowired
    KafkaTemplate<String, Object> kafka;


    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    MockMvc mvc;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Test
    void firesWithCooldownStoresUnreadEventsAndPublishesThem() throws Exception {
        Alert alert = repository.create(PriceTick.BTC_USD, Direction.ABOVE, new BigDecimal("9000000"), Instant.now());
        awaitEvaluatorIsAssigned();   // its group starts at "latest": only ticks sent after this are seen

        send("9000001", T0);                                            // fires
        send("9000002", T0.plus(Duration.ofMinutes(5)).minusMillis(1));  // in cooldown
        send("9000003", T0.plus(Duration.ofMinutes(5)));                // cooldown over, still above -> fires
        send("100", T0.plus(Duration.ofMinutes(11)));                   // cooldown over, not above -> nothing
        kafka.flush();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(eventsOf(alert.id())).extracting(AlertEvent::price)
                        .usingElementComparator(BigDecimal::compareTo)
                        .containsExactlyInAnyOrder(new BigDecimal("9000001"), new BigDecimal("9000003")));
        Thread.sleep(2_000);
        assertThat(eventsOf(alert.id())).as("no extra firings").hasSize(2);
        assertThat(repository.find(alert.id()).orElseThrow().lastTriggeredAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));

        // AC8: nobody was watching, so both are unread; the page shows them on the next visit.
        List<Long> ids = eventsOf(alert.id()).stream().map(AlertEvent::id).toList();
        for (long id : ids) {
            mvc.perform(get("/api/alert-events").param("unread", "true")).andExpect(jsonPath("$[*].id", hasItem((int) id)));
        }
        // The SSE side is told through Kafka.
        assertThat(publishedEventIds()).containsAll(ids);

        mvc.perform(post("/api/alert-events/{id}/read", ids.getFirst())).andExpect(status().isNoContent());
        mvc.perform(get("/api/alert-events").param("unread", "true"))
                .andExpect(jsonPath("$[*].id", not(hasItem(ids.getFirst().intValue()))));

        repository.delete(alert.id());
    }

    private void send(String price, Instant eventTime) {
        PriceTick tick = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal(price), "coinbase",
                eventTime, eventTime);
        kafka.send(Topics.PRICE_TICKS, tick.pair(), tick);
    }

    private List<AlertEvent> eventsOf(long alertId) {
        return repository.events(false, 1_000).stream().filter(e -> e.alertId() == alertId).toList();
    }

    /**
     * This context's evaluator owns the tick partitions. Its group id carries a per-context random
     * prefix (note: ${random.uuid} yields a new value on every resolution, so the id is read from the
     * container itself rather than recomputed), so no other cached test context can take them.
     */
    private void awaitEvaluatorIsAssigned() {
        MessageListenerContainer evaluator = registry.getListenerContainer("alert-evaluator");
        assertThat(evaluator).isNotNull();
        assertThat(evaluator.getGroupId()).startsWith("test-").endsWith("alert-evaluator");
        await().atMost(Duration.ofSeconds(60)).until(() ->
                evaluator.getAssignedPartitions() != null && !evaluator.getAssignedPartitions().isEmpty());
    }

    private List<Long> publishedEventIds() {
        List<Long> ids = new ArrayList<>();
        Properties earliest = new Properties();
        earliest.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("alerts-" + UUID.randomUUID(), null, null, earliest)) {
            consumer.subscribe(List.of(Topics.ALERTS_TRIGGERED));
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(300))) {
                    String v = r.value();
                    ids.add(Long.parseLong(v.replaceAll(".*\"eventId\":(\\d+).*", "$1")));
                }
                if (!ids.isEmpty()) {
                    break;
                }
            }
        }
        return ids;
    }
}
