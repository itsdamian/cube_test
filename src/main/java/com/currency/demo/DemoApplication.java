package com.currency.demo;

import com.currency.demo.model.Currency;
import com.currency.demo.repository.CurrencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.currency.demo",
    "com.currency.demo.controller",
    "com.currency.demo.service",
    "com.currency.demo.config"
})
@EntityScan("com.currency.demo.model")
@EnableJpaRepositories("com.currency.demo.repository")
public class DemoApplication {

    private static final Logger logger = LoggerFactory.getLogger(DemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
    
    @Bean
    @Profile("!test") // Only initialize data in non-test environments
    public CommandLineRunner initData(CurrencyRepository currencyRepository) {
        return args -> {
            logger.info("Initializing currency data...");
            // Initialize some example data
            currencyRepository.save(new Currency("USD", "美金"));
            currencyRepository.save(new Currency("EUR", "歐元"));
            currencyRepository.save(new Currency("JPY", "日圓"));
            currencyRepository.save(new Currency("GBP", "英鎊"));
            currencyRepository.save(new Currency("CNY", "人民幣"));
            currencyRepository.save(new Currency("HKD", "港幣"));
            currencyRepository.save(new Currency("AUD", "澳幣"));
            currencyRepository.save(new Currency("CAD", "加幣"));
            currencyRepository.save(new Currency("SGD", "新加坡幣"));
            currencyRepository.save(new Currency("CHF", "瑞士法郎"));
            logger.info("Currency data initialization completed.");
        };
    }
} 