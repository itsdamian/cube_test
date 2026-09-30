package com.currency.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system clock as a bean.
 *
 * <p>Classes that need "now" inject {@link Clock} instead of calling
 * {@code Instant.now()} directly, so tests can substitute a fixed or mutable clock
 * and check time-based rules (failover, alert cooldown, retention) without sleeping.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
