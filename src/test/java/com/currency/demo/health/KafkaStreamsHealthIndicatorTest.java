package com.currency.demo.health;

import org.apache.kafka.streams.KafkaStreams;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.actuate.health.Status;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaStreamsHealthIndicatorTest {

    @ParameterizedTest
    @EnumSource(KafkaStreams.State.class)
    void onlyRunningAndRebalancingAreUp(KafkaStreams.State state) {
        StreamsBuilderFactoryBean factory = mock(StreamsBuilderFactoryBean.class);
        KafkaStreams streams = mock(KafkaStreams.class);
        when(factory.getKafkaStreams()).thenReturn(streams);
        when(streams.state()).thenReturn(state);

        Status status = new KafkaStreamsHealthIndicator(factory).health().getStatus();

        boolean up = state == KafkaStreams.State.RUNNING || state == KafkaStreams.State.REBALANCING;
        assertThat(status).isEqualTo(up ? Status.UP : Status.DOWN);
    }
}
