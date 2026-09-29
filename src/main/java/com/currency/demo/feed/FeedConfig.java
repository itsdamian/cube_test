package com.currency.demo.feed;

import com.currency.demo.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;

/**
 * Beans for the price-feed side (ingest).
 *
 * <p>{@link FeedManager} and the exchange connections exist only when
 * {@code app.ingest.enabled=true}. Tests switch it off (no real exchange is ever
 * contacted), and in Kubernetes it will let ingest run as exactly one replica while the
 * API scales out.
 */
@Configuration(proxyBeanMethods = false)
public class FeedConfig {

    @Bean
    TickPublisher tickPublisher(KafkaOperations<String, Object> kafka, AppProperties props, MeterRegistry registry) {
        return new TickPublisher(kafka, props.feed().publishQueueCapacity(), registry);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ingest.enabled", havingValue = "true")
    FeedManager feedManager(AppProperties props, TickPublisher tickPublisher,
                            KafkaOperations<String, Object> kafka, Clock clock) {
        AppProperties.Feed feed = props.feed();
        HttpClient httpClient = HttpClient.newHttpClient();
        FeedManager.ClientFactory factory = (parser, listener) -> new WebSocketPriceFeedClient(
                urlFor(parser, feed), parser, listener, httpClient, clock,
                feed.idleTimeout(), feed.reconnectInitialBackoff(), feed.reconnectMaxBackoff());
        return new FeedManager(factory, new CoinbaseMessageParser(), new KrakenMessageParser(),
                tickPublisher, new FeedStatusPublisher(kafka), clock,
                feed.staleThreshold(), feed.priceStaleThreshold(), feed.recoveryPeriod());
    }

    private static URI urlFor(FeedMessageParser parser, AppProperties.Feed feed) {
        if (parser.sourceName().equals(feed.primary().name())) {
            return feed.primary().url();
        }
        if (parser.sourceName().equals(feed.backup().name())) {
            return feed.backup().url();
        }
        throw new IllegalStateException("No URL configured for source " + parser.sourceName());
    }
}
