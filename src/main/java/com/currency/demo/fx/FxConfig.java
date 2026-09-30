package com.currency.demo.fx;

import com.currency.demo.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class FxConfig {

    @Bean
    ExchangeRateClient exchangeRateClient(RestClient.Builder builder, AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new ExchangeRateClient(builder.requestFactory(factory).build(), props.fx().url());
    }

    /** Only when {@code app.fx.refresh-enabled=true} (off in tests: they must not call the real source). */
    @Bean
    @ConditionalOnProperty(name = "app.fx.refresh-enabled", havingValue = "true")
    FxRateRefresher fxRateRefresher(ExchangeRateClient client, FxRateRepository repository, Clock clock,
                                    TaskScheduler taskScheduler) {
        return new FxRateRefresher(client, repository, clock, taskScheduler);
    }
}
