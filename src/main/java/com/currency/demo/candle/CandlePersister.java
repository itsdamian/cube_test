package com.currency.demo.candle;

import com.currency.demo.config.Topics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Stores finalised candles from {@code btc.candles} (shared group: each candle written once). */
@Component
@ConditionalOnProperty(name = "app.persist.enabled", havingValue = "true")
public class CandlePersister {

    private final CandleRepository repository;

    public CandlePersister(CandleRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(id = "candle-persister", groupId = "${app.kafka.group-prefix}candle-persister", topics = Topics.CANDLES,
            containerFactory = "candleBatchListenerFactory", properties = "auto.offset.reset=earliest")
    public void persist(List<Candle> batch) {
        repository.upsertAll(batch.stream().filter(Objects::nonNull).toList());
    }
}
