package com.currency.demo.feed;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.alert.AlertRepository;
import com.currency.demo.alert.Direction;
import com.currency.demo.candle.CandleRepository;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.stream.SseBroadcaster;
import com.currency.demo.support.IntegrationTest;
import com.currency.demo.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaOperations;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole backend in one flow (QA T2): ingest -> Kafka -> tick DB, Kafka Streams candles ->
 * candle DB, alert evaluation, SSE push, and failover - with only the two exchanges replaced by
 * test-controlled fakes and time driven by a mutable clock (event times in 2035).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.streams.enabled=true")
class EndToEndPipelineTest extends IntegrationTest {

    static final Instant T0 = Instant.parse("2035-01-01T00:00:00Z");
    static final MutableClock CLOCK = new MutableClock(T0);
    static final Map<String, FakeFeedClient> EXCHANGES = new HashMap<>();

    /** Ingest itself stays real (FeedManager, TickPublisher, status publishing); only the sockets are fake. */
    @TestConfiguration
    static class FakeExchanges {
        @Bean
        FeedManager feedManager(TickPublisher tickPublisher, KafkaOperations<String, Object> kafka) {
            return new FeedManager((parser, listener) -> {
                FakeFeedClient fake = new FakeFeedClient(parser.sourceName(), listener, CLOCK);
                EXCHANGES.put(parser.sourceName(), fake);
                return fake;
            }, new CoinbaseMessageParser(), new KrakenMessageParser(), tickPublisher, new FeedStatusPublisher(kafka),
                    CLOCK, Duration.ofSeconds(10), Duration.ofSeconds(60), Duration.ofSeconds(15));
        }
    }

    record Event(String name, JsonNode data) {
    }

    @LocalServerPort
    int port;

    @Autowired
    FeedManager manager;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CandleRepository candles;

    @Autowired
    AlertRepository alerts;

    @Autowired
    HealthEndpoint health;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    SseBroadcaster broadcaster;

    @Autowired
    ObjectMapper json;

    private final List<Event> events = new CopyOnWriteArrayList<>();

    @Test
    void ticksFlowToDatabaseCandlesAlertsAndBrowserAndFailoverIsPushed() throws Exception {
        FakeFeedClient coinbase = EXCHANGES.get("coinbase");
        FakeFeedClient kraken = EXCHANGES.get("kraken");
        Alert alert = alerts.create(PriceTick.BTC_USD, Direction.ABOVE, new BigDecimal("8450000"), Instant.now());
        InputStream sse = openBrowserStream();
        try {
            awaitConsumersReady();

            // 12 minutes, one step per 5 s of event time, both exchanges ticking (hot standby).
            for (int i = 0; i < 144; i++) {
                CLOCK.advance(Duration.ofSeconds(5));
                BigDecimal price = BigDecimal.valueOf(8_400_000L + 1_000L * i);   // crosses 8,450,000 at i = 51
                coinbase.tick(price);
                kraken.tick(price.add(BigDecimal.ONE));
                manager.check();
            }
            CLOCK.advance(Duration.ofMinutes(1));   // one more tick to close the last windows
            coinbase.tick(new BigDecimal("8400000"));
            kraken.tick(new BigDecimal("8400000"));
            manager.check();

            Instant from = T0;
            Instant to = T0.plus(Duration.ofMinutes(12));
            await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                // Only the ACTIVE source (coinbase) is published. Ticks are at T0+5 s ... T0+720 s;
                // [T0, T0+12 min) excludes the one exactly at 12:00 -> 143.
                assertThat(count("SELECT count(*) FROM price_tick WHERE event_time >= ? AND event_time < ? AND source = 'coinbase'", from, to))
                        .isEqualTo(143);
                assertThat(candles.find(PriceTick.BTC_USD, "1m", from, to)).hasSizeGreaterThanOrEqualTo(10);
                assertThat(candles.find(PriceTick.BTC_USD, "5m", from, to)).hasSizeGreaterThanOrEqualTo(2);
                // Fires at i = 51 and again 5 minutes of event time later (still above): 2 unread events.
                assertThat(count("SELECT count(*) FROM alert_event WHERE alert_id = ? AND read_at IS NULL", alert.id()))
                        .isEqualTo(2);
                assertThat(named("price")).isNotEmpty();
                assertThat(named("alert")).anySatisfy(e -> assertThat(e.data().get("alertId").asLong()).isEqualTo(alert.id()));
            });
            assertThat(count("SELECT count(*) FROM price_tick WHERE event_time >= ? AND event_time < ? AND source = 'kraken'", from, to))
                    .as("hot standby is not published").isZero();

            // Failover: coinbase goes silent, kraken keeps trading -> status pushed with activeSource=kraken.
            for (int s = 0; s < 11; s++) {
                CLOCK.advance(Duration.ofSeconds(1));
                kraken.tick(new BigDecimal("8400100"));
                manager.check();
            }
            assertThat(manager.activeSource()).isEqualTo("kraken");
            await().atMost(Duration.ofSeconds(10)).until(() -> named("status").stream()
                    .anyMatch(e -> e.data().get("activeSource").asText().equals("kraken")));

            // Coinbase recovers; after 15 s of health it is the active source again, and the browser is told.
            for (int s = 0; s < 16; s++) {
                CLOCK.advance(Duration.ofSeconds(1));
                coinbase.tick(new BigDecimal("8400200"));
                kraken.tick(new BigDecimal("8400201"));
                manager.check();
            }
            assertThat(manager.activeSource()).isEqualTo("coinbase");
            int krakenIndex = lastIndexOfStatus("kraken");
            await().atMost(Duration.ofSeconds(10)).until(() -> lastIndexOfStatus("coinbase") > krakenIndex);
        } finally {
            sse.close();
            alerts.delete(alert.id());
        }
    }

    private InputStream openBrowserStream() throws Exception {
        int before = broadcaster.connectionCount();
        HttpResponse<InputStream> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/stream")).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        InputStream body = response.body();
        Thread.ofVirtual().start(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                String name = null;
                for (String line; (line = lines.readLine()) != null; ) {
                    if (line.startsWith("event:")) {
                        name = line.substring(6).trim();
                    } else if (line.startsWith("data:") && name != null) {
                        events.add(new Event(name, json.readTree(line.substring(5))));
                        name = null;
                    }
                }
            } catch (Exception closed) {
                // test finished
            }
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> broadcaster.connectionCount() > before);
        return body;
    }

    /** SSE consumers and Kafka Streams start at "latest": wait until they are live before producing. */
    private void awaitConsumersReady() {
        await().atMost(Duration.ofSeconds(60)).until(() ->
                health.healthForPath("readiness", "kafkaStreams").getStatus() == Status.UP
                        && registry.getAllListenerContainers().stream()
                        .filter(c -> c.getGroupId() != null && c.getGroupId().startsWith("sse-"))
                        .allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
    }

    private List<Event> named(String name) {
        return events.stream().filter(e -> e.name().equals(name)).toList();
    }

    private int lastIndexOfStatus(String source) {
        List<Event> snapshot = List.copyOf(events);
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            Event e = snapshot.get(i);
            if (e.name().equals("status") && e.data().get("activeSource").asText().equals(source)) {
                return i;
            }
        }
        return -1;
    }

    private int count(String sql, Object... args) {
        Object[] converted = java.util.Arrays.stream(args)
                .map(a -> a instanceof Instant i ? i.atOffset(java.time.ZoneOffset.UTC) : a).toArray();
        return jdbc.queryForObject(sql, Integer.class, converted);
    }
}
