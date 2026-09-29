package com.currency.demo.pricing;

import com.currency.demo.config.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Writes every tick from {@code btc.price.ticks} to PostgreSQL.
 *
 * <p>Batch listener in the shared consumer group {@code tick-persister}: however many app
 * instances run, each tick is stored once. Offsets are committed after the batch returns,
 * so a crash means redelivery (at-least-once) - harmless thanks to the unique event id.
 * {@code auto.offset.reset=earliest} makes a brand-new group start from the oldest retained
 * tick instead of skipping what was produced before it first connected.
 */
@Component
@ConditionalOnProperty(name = "app.persist.enabled", havingValue = "true")
public class TickPersister {

    private static final Logger log = LoggerFactory.getLogger(TickPersister.class);

    private final PriceTickRepository repository;

    public TickPersister(PriceTickRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(id = "tick-persister", groupId = "tick-persister", topics = Topics.PRICE_TICKS,
            containerFactory = "tickBatchListenerFactory",
            properties = {"auto.offset.reset=earliest", "max.poll.records=500"})
    public void persist(List<PriceTick> batch) {
        // Records that could not be deserialized arrive as null (ErrorHandlingDeserializer).
        List<PriceTick> ticks = batch.stream().filter(Objects::nonNull).toList();
        if (ticks.size() < batch.size()) {
            log.warn("Skipped {} unreadable tick record(s)", batch.size() - ticks.size());
        }
        repository.insertAll(ticks);
    }
}
