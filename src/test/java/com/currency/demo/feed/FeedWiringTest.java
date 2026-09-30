package com.currency.demo.feed;

import com.currency.demo.config.Topics;
import com.currency.demo.support.IntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * With {@code app.ingest.enabled=true}, Spring wires FeedManager + both real WebSocket clients.
 * The exchanges are local fakes on 127.0.0.1, so nothing leaves the machine; we check that
 * the primary's ticks reach {@code btc.price.ticks} and a LIVE status reaches {@code btc.feed.status}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "app.ingest.enabled=true")
class FeedWiringTest extends IntegrationTest {

    static final FakeExchangeServer COINBASE = start("coinbase/ticker.json");
    static final FakeExchangeServer KRAKEN = start("kraken/trade-multi.json");

    @DynamicPropertySource
    static void exchanges(DynamicPropertyRegistry registry) {
        registry.add("app.feed.primary.url", () -> COINBASE.uri().toString());
        registry.add("app.feed.backup.url", () -> KRAKEN.uri().toString());
    }

    @AfterAll
    static void stopServers() throws InterruptedException {
        COINBASE.shutdown();
        KRAKEN.shutdown();
    }

    @Autowired
    FeedManager feedManager;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    // Exact event times of the fixtures: other tests also write "coinbase" ticks and statuses to the
    // same topics, so only records that can only come from THIS context's fake exchanges count.
    static final String COINBASE_FIXTURE_TIME = "\"eventTime\":\"2026-09-29T07:37:31.089264Z\"";
    static final String KRAKEN_FIXTURE_TIME = "\"eventTime\":\"2026-09-29T07:37:33.786450Z\"";

    @Test
    void ingestPublishesPrimaryTicksAndLiveStatusToKafka() {
        assertThat(feedManager.isRunning()).isTrue();
        await().atMost(Duration.ofSeconds(30)).until(() -> feedManager.currentStatus() != null
                && feedManager.currentStatus().state() == FeedStatus.State.LIVE);
        assertThat(feedManager.clients()).extracting(PriceFeedClient::sourceName).containsExactly("coinbase", "kraken");

        List<String> ticks = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        Properties earliest = new Properties();
        earliest.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("wiring-" + UUID.randomUUID(), null, null, earliest)) {
            consumer.subscribe(List.of(Topics.PRICE_TICKS, Topics.FEED_STATUS));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(200))) {
                    (r.topic().equals(Topics.PRICE_TICKS) ? ticks : statuses).add(r.value());
                }
                return ticks.stream().anyMatch(v -> v.contains(COINBASE_FIXTURE_TIME))
                        && statuses.stream().anyMatch(v -> v.contains("\"activeSource\":\"coinbase\"")
                        && v.contains("\"state\":\"LIVE\""));
            });
        }
        // Hot standby: kraken is connected and ticking too, but only the active source is published.
        assertThat(ticks).noneMatch(v -> v.contains(KRAKEN_FIXTURE_TIME));
        assertThat(feedManager.currentStatus().activeSource()).isEqualTo("coinbase");
    }

    private static FakeExchangeServer start(String fixture) {
        try {
            FakeExchangeServer server = new FakeExchangeServer(Fixtures.read(fixture));
            server.startAndWait();
            server.pumping(true);
            return server;
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
