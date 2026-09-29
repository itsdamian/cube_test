package com.currency.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>{@code @SpringBootApplication} already component-scans this package and every
 * sub-package, and auto-configures JPA entity/repository scanning from here, so no
 * explicit {@code @ComponentScan}/{@code @EntityScan} is needed.
 * {@code @ConfigurationPropertiesScan} registers our {@code @ConfigurationProperties}
 * records (see {@link com.currency.demo.config.AppProperties}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
