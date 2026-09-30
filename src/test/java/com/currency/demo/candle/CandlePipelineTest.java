package com.currency.demo.candle;

import com.currency.demo.DemoApplication;
import com.currency.demo.config.Topics;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End to end with real Kafka, Kafka Streams and PostgreSQL (AC4, AC5):
 * ticks -> btc.price.ticks -> (TickPersister -> price_tick) and (CandleTopology -> btc.candles
 * -> CandlePersister -> candle). Every stored candle must equal the same aggregate computed in
 * SQL from the stored ticks, using the canonical order (event_time, received_at, event_id) -
 * the query README gives for the manual AC4 check. No @Transactional: data is really committed
 * and then read by a second, freshly started application instance (AC5).
 * Event times are in 2032 so they never mix with other tests' ticks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "app.streams.enabled=true")
class CandlePipelineTest extends IntegrationTest {

    static final Instant T0 = Instant.parse("2032-03-01T12:00:00Z");

    /** AC4 cross-check: the candle for [from, to) computed directly from price_tick. */
    static final String OHLC_FROM_TICKS = """
            SELECT (array_agg(price ORDER BY event_time, received_at, event_id))[1]                 AS open,
                   max(price)                                                                        AS high,
                   min(price)                                                                        AS low,
                   (array_agg(price ORDER BY event_time DESC, received_at DESC, event_id DESC))[1]  AS close,
                   count(*)                                                                          AS tick_count
            FROM price_tick
            WHERE pair = 'BTC-USD' AND event_time >= ? AND event_time < ?
            """;

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CandleRepository candles;

    @Autowired
    org.springframework.boot.actuate.health.HealthEndpoint health;

    @Test
    void storedCandlesMatchStoredTicksAndSurviveAnApplicationRestart() {
        Random random = new Random(7);
        for (int s = 0; s < 12 * 60; s += 5) {
            Instant t = T0.plusSeconds(s);
            send(t, BigDecimal.valueOf(random.nextInt(500_000), 2).add(new BigDecimal("80000")), t.plusMillis(25), UUID.randomUUID());
            if (s % 60 == 30) {
                // Same eventTime and receivedAt: only the event id decides the order. The ids differ in
                // their top bit (unsigned vs signed order disagree) and are unique per minute - reusing
                // one id would be de-duplicated by the database but counted again by Streams.
                long minute = s / 60;
                send(t, new BigDecimal("79999.99"), t.plusMillis(25), new UUID(0xF000000000000000L | minute, minute));
                send(t, new BigDecimal("90000.01"), t.plusMillis(25), new UUID(0x0F00000000000000L | minute, minute));
            }
        }
        send(T0.plusSeconds(13 * 60), new BigDecimal("80000"), T0.plusSeconds(13 * 60), UUID.randomUUID()); // close windows
        kafka.flush();

        Instant end = T0.plusSeconds(12 * 60);
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertThat(candles.find(PriceTick.BTC_USD, "1m", T0, end)).hasSize(12);
            assertThat(candles.find(PriceTick.BTC_USD, "5m", T0, end)).hasSize(2);
        });

        // With Streams enabled the readiness group includes kafkaStreams, which is RUNNING now.
        assertThat(health.healthForPath("readiness").getStatus()).isEqualTo(org.springframework.boot.actuate.health.Status.UP);
        assertThat(health.healthForPath("readiness", "kafkaStreams").getStatus())
                .isEqualTo(org.springframework.boot.actuate.health.Status.UP);

        List<Candle> stored = new java.util.ArrayList<>(candles.find(PriceTick.BTC_USD, "1m", T0, end));
        stored.addAll(candles.find(PriceTick.BTC_USD, "5m", T0, end));
        for (Candle c : stored) {
            Map<String, Object> fromTicks = jdbc.queryForMap(OHLC_FROM_TICKS, utc(c.openTime()), utc(c.closeTime()));
            assertThat(c.open()).as("%s %s open", c.interval(), c.openTime()).isEqualByComparingTo((BigDecimal) fromTicks.get("open"));
            assertThat(c.high()).as("%s %s high", c.interval(), c.openTime()).isEqualByComparingTo((BigDecimal) fromTicks.get("high"));
            assertThat(c.low()).as("%s %s low", c.interval(), c.openTime()).isEqualByComparingTo((BigDecimal) fromTicks.get("low"));
            assertThat(c.close()).as("%s %s close", c.interval(), c.openTime()).isEqualByComparingTo((BigDecimal) fromTicks.get("close"));
            assertThat((long) c.tickCount()).as("%s %s count", c.interval(), c.openTime()).isEqualTo(fromTicks.get("tick_count"));
        }

        // AC5: a brand-new application instance (fresh JVM state, same database) still serves the data.
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(DemoApplication.class)
                .profiles("test")
                .run("--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
                        "--app.persist.enabled=false")) {
            int port = ((WebServerApplicationContext) restarted).getWebServer().getPort();
            RestClient http = RestClient.create("http://localhost:" + port);

            JsonNode oneMinute = http.get().uri("/api/candles?interval=1m&from={f}&to={t}", T0, end)
                    .retrieve().body(JsonNode.class);
            JsonNode fiveMinute = http.get().uri("/api/candles?interval=5m&from={f}&to={t}", T0, end)
                    .retrieve().body(JsonNode.class);
            JsonNode history = http.get().uri("/api/prices/history?from={f}&to={t}&limit=5000", T0, end)
                    .retrieve().body(JsonNode.class);

            assertThat(oneMinute).hasSize(12);
            assertThat(fiveMinute).hasSize(2);
            assertThat(history.get("items")).hasSize(144 + 24);   // 144 regular + 2 ties x 12 minutes
        }
    }

    private void send(Instant eventTime, BigDecimal price, Instant receivedAt, UUID id) {
        PriceTick tick = new PriceTick(id, PriceTick.BTC_USD, price, "coinbase", eventTime, receivedAt);
        kafka.send(Topics.PRICE_TICKS, tick.pair(), tick);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
