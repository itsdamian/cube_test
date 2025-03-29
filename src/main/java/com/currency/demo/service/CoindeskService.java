package com.currency.demo.service;

import com.currency.demo.model.Currency;
import com.currency.demo.repository.CurrencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class CoindeskService {

    private static final Logger logger = LoggerFactory.getLogger(CoindeskService.class);
    private static final String COINDESK_API_URL = "https://api.coindesk.com/v1/bpi/currentprice.json";
    
    private final RestTemplate restTemplate;
    private final CurrencyRepository currencyRepository;
    
    @Autowired
    public CoindeskService(RestTemplate restTemplate, CurrencyRepository currencyRepository) {
        this.restTemplate = restTemplate;
        this.currencyRepository = currencyRepository;
    }
    
    /**
     * Get original Coindesk API data
     */
    public Map<String, Object> getOriginalData() {
        try {
            logger.info("Calling Coindesk API to get original data");
            return restTemplate.getForObject(COINDESK_API_URL, Map.class);
        } catch (Exception e) {
            logger.error("Failed to get Coindesk API data, using mock data", e);
            return createMockData();
        }
    }
    
    /**
     * Create mock data when API is not accessible
     */
    private Map<String, Object> createMockData() {
        logger.info("Creating mock Bitcoin price data");
        Map<String, Object> mockData = new HashMap<>();
        
        // Time information
        Map<String, Object> time = new HashMap<>();
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        String now = isoFormat.format(new Date());
        time.put("updated", "Mar 29, 2025 11:53:00 UTC");
        time.put("updatedISO", now);
        time.put("updateduk", "Mar 29, 2025 at 11:53 BST");
        mockData.put("time", time);
        
        // Disclaimer
        mockData.put("disclaimer", "This data was produced from the CoinDesk Bitcoin Price Index (USD). Non-USD currency data converted using hourly conversion rate from openexchangerates.org");
        mockData.put("chartName", "Bitcoin");
        
        // BPI (Bitcoin Price Index)
        Map<String, Object> bpi = new HashMap<>();
        
        // USD
        Map<String, Object> usd = new HashMap<>();
        usd.put("code", "USD");
        usd.put("symbol", "&dollar;");
        usd.put("rate", "57,231.4983");
        usd.put("description", "United States Dollar");
        usd.put("rate_float", 57231.4983);
        bpi.put("USD", usd);
        
        // GBP
        Map<String, Object> gbp = new HashMap<>();
        gbp.put("code", "GBP");
        gbp.put("symbol", "&pound;");
        gbp.put("rate", "42,345.8722");
        gbp.put("description", "British Pound Sterling");
        gbp.put("rate_float", 42345.8722);
        bpi.put("GBP", gbp);
        
        // EUR
        Map<String, Object> eur = new HashMap<>();
        eur.put("code", "EUR");
        eur.put("symbol", "&euro;");
        eur.put("rate", "49,876.1232");
        eur.put("description", "Euro");
        eur.put("rate_float", 49876.1232);
        bpi.put("EUR", eur);
        
        mockData.put("bpi", bpi);
        
        return mockData;
    }
    
    /**
     * Get transformed Bitcoin price data
     */
    public Map<String, Object> getTransformedData() {
        logger.info("Starting Bitcoin price data transformation");
        // Get original data
        Map<String, Object> originalData = getOriginalData();
        
        // Transformed data
        Map<String, Object> transformedData = new HashMap<>();
        
        // 1. Update time (format as 1990/01/01 00:00:00)
        String updateTime = formatUpdateTime(originalData);
        logger.debug("Formatted update time: {}", updateTime);
        transformedData.put("updateTime", updateTime);
        
        // 2. Currency information
        Map<String, Object> currencies = new HashMap<>();
        
        // Get BPI information from original data
        Map<String, Object> bpi = (Map<String, Object>) originalData.get("bpi");
        
        // Process each currency
        for (String code : bpi.keySet()) {
            Map<String, Object> currencyInfo = (Map<String, Object>) bpi.get(code);
            Double rate = parseRate(currencyInfo.get("rate").toString());
            
            // Get Chinese name for the currency
            Currency currency = currencyRepository.findByCode(code);
            String chineseName = (currency != null) ? currency.getName() : code + " (No Chinese name)";
            logger.debug("Processing currency: {}, Chinese name: {}, rate: {}", code, chineseName, rate);
            
            // Assemble currency information
            Map<String, Object> currencyData = new HashMap<>();
            currencyData.put("code", code);
            currencyData.put("chineseName", chineseName);
            currencyData.put("rate", rate);
            
            currencies.put(code, currencyData);
        }
        
        transformedData.put("currencies", currencies);
        logger.info("Completed Bitcoin price data transformation");
        
        return transformedData;
    }
    
    /**
     * Format update time
     */
    private String formatUpdateTime(Map<String, Object> originalData) {
        try {
            Map<String, Object> time = (Map<String, Object>) originalData.get("time");
            String updatedISO = (String) time.get("updatedISO");
            
            // Parse ISO time
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
            Date date = isoFormat.parse(updatedISO);
            
            // Format to 1990/01/01 00:00:00
            SimpleDateFormat targetFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            return targetFormat.format(date);
        } catch (Exception e) {
            logger.error("Failed to format time", e);
            return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
        }
    }
    
    /**
     * Parse rate string to number
     */
    private Double parseRate(String rateStr) {
        try {
            // Remove thousand separators and other non-numeric characters
            return Double.parseDouble(rateStr.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            logger.error("Failed to parse rate: " + rateStr, e);
            return 0.0;
        }
    }
} 