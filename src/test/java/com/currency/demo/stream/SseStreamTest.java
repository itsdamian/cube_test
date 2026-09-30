package com.currency.demo.stream;

import com.currency.demo.alert.AlertDtos.AlertTriggered;
import com.currency.demo.alert.Direction;
import com.currency.demo.config.Topics;
import com.currency.demo.feed.FeedStatus;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A real HTTP client reading {@code /api/stream} while events are written to Kafka.
 * Prices in the 7,000,000 range identify this test's ticks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SseStreamTest extends IntegrationTest {

    record Event(String name, JsonNode data, long receivedAtMillis) {
    }

    @LocalServerPort
    int port;

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    SseBroadcaster broadcaster;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    ObjectMapper json;

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private InputStream body;
    private Thread reader;
    private int connectionsBefore;

    @BeforeEach
    void connect() throws Exception {
        // A previous test's closed connection is only noticed on the next write, so count relatively.
        connectionsBefore = broadcaster.connectionCount();
        HttpResponse<InputStream> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/stream")).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).get().asString().startsWith("text/event-stream");
        body = response.body();
        reader = Thread.ofVirtual().start(this::readEvents);
        await().atMost(Duration.ofSeconds(5)).until(() -> broadcaster.connectionCount() == connectionsBefore + 1);
        // The SSE consumers start at "latest": wait until they own their partitions before producing.
        await().atMost(Duration.ofSeconds(30)).until(() -> registry.getAllListenerContainers().stream()
                .filter(c -> c.getGroupId() != null && c.getGroupId().startsWith("sse-"))
                .allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));
    }

    @AfterEach
    void disconnect() throws Exception {
        body.close();
        reader.interrupt();
    }

    /** Minimal SSE parser: "event:" + "data:" lines, blank line = end of event, ":" = comment. */
    private void readEvents() {
        try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(body, StandardCharsets.UTF_8))) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line; (line = lines.readLine()) != null; ) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5));
                } else if (line.isEmpty() && name != null) {
                    events.add(new Event(name, json.readTree(data.toString()), System.currentTimeMillis()));
                    name = null;
                    data.setLength(0);
                }
            }
        } catch (Exception e) {
            // stream closed by the test
        }
    }

    private void sendTick(String price) {
        Instant now = Instant.now();
        PriceTick tick = new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, new BigDecimal(price), "coinbase", now, now);
        kafka.send(Topics.PRICE_TICKS, tick.pair(), tick);
    }

    private List<Event> named(String name) {
        return events.stream().filter(e -> e.name().equals(name)).toList();
    }

    @Test
    void priceStatusAndAlertEventsArriveWithinFiveSeconds() {
        Instant now = Instant.now();
        kafka.send(Topics.FEED_STATUS, "BTC-USD", new FeedStatus("kraken", FeedStatus.State.LIVE, now, now));
        sendTick("7000123.45");
        kafka.send(Topics.ALERTS_TRIGGERED, "BTC-USD", new AlertTriggered(4242, 7, "BTC-USD", Direction.ABOVE,
                new BigDecimal("7000000"), new BigDecimal("7000123.45"), now));
        kafka.flush();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(named("status")).anySatisfy(e -> assertThat(e.data().get("activeSource").asText()).isEqualTo("kraken"));
            assertThat(named("price")).anySatisfy(e -> assertThat(e.data().get("price").decimalValue())
                    .isEqualByComparingTo("7000123.45"));
            assertThat(named("alert")).anySatisfy(e -> assertThat(e.data().get("eventId").asLong()).isEqualTo(4242));
        });
    }

    @Test
    void aBurstOfFiftyTicksInOneSecondIsThrottledButTheLastPriceIsDelivered() throws Exception {
        long start = System.currentTimeMillis();
        for (int i = 1; i <= 50; i++) {
            sendTick(String.valueOf(7_000_000 + i));   // async send; ~50 ticks in ~1 s
            Thread.sleep(19);
        }
        kafka.flush();
        long end = System.currentTimeMillis();

        await().atMost(Duration.ofSeconds(5)).until(() -> named("price").stream()
                .anyMatch(e -> e.data().get("price").decimalValue().compareTo(new BigDecimal("7000050")) == 0));
        Thread.sleep(600); // nothing more may follow once the last price was delivered

        List<Event> burst = named("price").stream()
                .filter(e -> e.data().get("price").decimalValue().compareTo(new BigDecimal("7000000")) > 0)
                .filter(e -> e.data().get("price").decimalValue().compareTo(new BigDecimal("7000050")) <= 0)
                .toList();
        // At most one price push per 250 ms: for a burst of D ms that is ceil(D / 250) + 1 events
        // (5 for a one-second burst), instead of 50.
        long allowed = (long) Math.ceil((end - start) / (double) SseBroadcaster.PRICE_INTERVAL_MS) + 1;
        assertThat((long) burst.size()).as("burst of %d ms", end - start).isLessThanOrEqualTo(allowed);
        for (int i = 1; i < burst.size(); i++) {
            assertThat(burst.get(i).receivedAtMillis() - burst.get(i - 1).receivedAtMillis())
                    .as("gap between price events (receive-side jitter tolerated)").isGreaterThanOrEqualTo(150);
        }
        assertThat(burst.getLast().data().get("price").decimalValue()).as("last price always delivered")
                .isEqualByComparingTo("7000050");
    }

    @Test
    void disconnectedClientIsRemoved() throws Exception {
        body.close();
        reader.interrupt();

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).until(() -> {
            Instant now = Instant.now();   // writing to the dead connection is what reveals it is gone
            kafka.send(Topics.FEED_STATUS, "BTC-USD", new FeedStatus("coinbase", FeedStatus.State.LIVE, now, now));
            return broadcaster.connectionCount() == 0;   // ours and any left over by earlier tests
        });
    }
}
