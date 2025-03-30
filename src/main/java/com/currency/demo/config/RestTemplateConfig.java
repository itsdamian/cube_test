package com.currency.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate timeout configuration
 */
@Configuration
public class RestTemplateConfig {
    
    /**
     * Create RestTemplate Bean with connection and read timeout settings
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Set connection timeout (5 seconds)
        factory.setConnectTimeout(5000);
        // Set read timeout (5 seconds)
        factory.setReadTimeout(5000);
        
        return new RestTemplate(factory);
    }
} 