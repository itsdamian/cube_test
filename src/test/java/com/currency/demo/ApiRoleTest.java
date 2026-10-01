package com.currency.demo;

import com.currency.demo.alert.AlertEvaluator;
import com.currency.demo.candle.CandlePersister;
import com.currency.demo.config.Topics;
import com.currency.demo.feed.FeedManager;
import com.currency.demo.feed.PriceFeedClient;
import com.currency.demo.fx.FxRateRefresher;
import com.currency.demo.pricing.PriceTick;
import com.currency.demo.pricing.RetentionJob;
import com.currency.demo.pricing.TickPersister;
import com.currency.demo.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.KafkaTemplate;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The "api" role of the Kubernetes deployment (spec k8s-gitops-cicd, plan: backend-api).
 * Exactly the switches the Deployment sets: every background role off, only REST + SSE.
 * Many of these replicas run behind the HPA, so none of them may ingest, stream candles,
 * persist, evaluate alerts or refresh FX rates - that is the single worker's job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ingest.enabled=false",
        "app.streams.enabled=false",
        "app.persist.enabled=false",
        "app.alerts.enabled=false",
        "app.fx.refresh-enabled=false",
})
class ApiRoleTest extends IntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @Autowired
    MeterRegistry meters;

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void noBackgroundRoleRunsInAnApiReplica() {
        for (Class<?> worker : List.of(FeedManager.class, PriceFeedClient.class, TickPersister.class,
                CandlePersister.class, AlertEvaluator.class, FxRateRefresher.class, RetentionJob.class,
                StreamsBuilderFactoryBean.class, KafkaStreams.class)) {
            assertThat(context.getBeanNamesForType(worker)).as(worker.getSimpleName()).isEmpty();
        }
        // No shared consumer group either: only the per-instance SSE / status groups remain.
        assertThat(listeners.getAllListenerContainers())
                .allSatisfy(c -> assertThat(c.getGroupId()).matches("(sse-|feed-status-).*"));
        assertThat(meters.get("cube.feed.ingest.active").gauge().value()).isZero();
    }

    @Test
    void isReadyWithoutKafkaStreamsAndServesRest() throws Exception {
        HttpResponse<String> readiness = get("/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"").contains("\"db\"").contains("\"kafka\"")
                .doesNotContain("kafkaStreams");
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);

        HttpResponse<String> currencies = get("/api/currencies");
        assertThat(currencies.statusCode()).isEqualTo(200);
        assertThat(currencies.body()).contains("\"code\"");
    }

    @Test
    void streamsPricesItReadsFromKafka() throws Exception {
        HttpResponse<InputStream> stream = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/stream")).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertThat(stream.statusCode()).isEqualTo(200);
        List<String> dataLines = new CopyOnWriteArrayList<>();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream.body(), StandardCharsets.UTF_8))) {
                for (String line; (line = lines.readLine()) != null; ) {
                    if (line.startsWith("data:")) {
                        dataLines.add(line);
                    }
                }
            } catch (Exception e) {
                // closed by the test
            }
        });
        try {
            // The SSE consumer starts at "latest": wait until it owns its partitions before producing.
            await().atMost(Duration.ofSeconds(30)).until(() -> listeners.getAllListenerContainers().stream()
                    .filter(c -> c.getGroupId() != null && c.getGroupId().startsWith("sse-ticks-"))
                    .allMatch(c -> c.getAssignedPartitions() != null && !c.getAssignedPartitions().isEmpty()));

            Instant now = Instant.now();
            kafka.send(Topics.PRICE_TICKS, PriceTick.BTC_USD, new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD,
                    new BigDecimal("8100123.45"), "coinbase", now, now));
            kafka.flush();

            await().atMost(Duration.ofSeconds(5)).until(() ->
                    dataLines.stream().anyMatch(l -> l.contains("8100123.45")));
        } finally {
            stream.body().close();
            reader.interrupt();
        }
    }
}
