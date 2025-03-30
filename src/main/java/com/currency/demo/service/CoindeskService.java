package com.currency.demo.service;

import com.currency.demo.model.Currency;
import com.currency.demo.repository.CurrencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoindeskService {
    private static final Logger log = LoggerFactory.getLogger(CoindeskService.class);
    private static final String COINDESK_API_URL = "https://api.coindesk.com/v1/bpi/currentprice.json";
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private CurrencyRepository currencyRepository;
    
    /**
     * Get original Bitcoin price data from Coindesk API
     */
    public Map<String, Object> getOriginalData() {
        log.info("Calling Coindesk API to get original data");
        Map<String, Object> response = null;
        
        try {
            // Call Coindesk API
            response = restTemplate.getForObject(COINDESK_API_URL, Map.class);
            
            // Validate if API response is complete
            if (!isValidResponse(response)) {
                log.warn("Coindesk API returned incomplete data");
                response = createDefaultData();
            }
        } catch (Exception e) {
            // API call failed, use default data
            log.error("Failed to get Coindesk API data, using mock data", e);
            response = createDefaultData();
        }
        
        return response;
    }
    
    /**
     * Validate if the API response contains required fields
     */
    private boolean isValidResponse(Map<String, Object> response) {
        if (response == null) return false;
        
        // Check if required fields exist
        if (!response.containsKey("bpi") || !response.containsKey("time")) {
            return false;
        }
        
        // Check if bpi is empty
        Map<String, Object> bpi = (Map<String, Object>) response.get("bpi");
        if (bpi == null || bpi.isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Create default Bitcoin price data (when API call fails)
     */
    private Map<String, Object> createDefaultData() {
        log.info("Creating mock Bitcoin price data");
        Map<String, Object> mockData = new HashMap<>();
        
        // Time information
        Map<String, Object> time = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime utcNow = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"));
        
        time.put("updated", utcNow.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")) + " UTC");
        time.put("updatedISO", utcNow.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        time.put("updateduk", utcNow.withZoneSameInstant(ZoneId.of("Europe/London"))
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")) + " BST");
        mockData.put("time", time);
        
        // Disclaimer
        mockData.put("disclaimer", "This data was produced from the CoinDesk Bitcoin Price Index (USD). "
                + "Non-USD currency data converted using hourly conversion rate from openexchangerates.org");
        mockData.put("chartName", "Bitcoin");
        
        // Bitcoin Price Index
        Map<String, Object> bpi = new HashMap<>();
        
        // USD
        Map<String, Object> usd = new HashMap<>();
        usd.put("code", "USD");
        usd.put("symbol", "&dollar;");
        usd.put("rate", "50,000.0000");
        usd.put("description", "United States Dollar");
        usd.put("rate_float", 50000.0000);
        bpi.put("USD", usd);
        
        // GBP
        Map<String, Object> gbp = new HashMap<>();
        gbp.put("code", "GBP");
        gbp.put("symbol", "&pound;");
        gbp.put("rate", "40,000.0000");
        gbp.put("description", "British Pound Sterling");
        gbp.put("rate_float", 40000.0000);
        bpi.put("GBP", gbp);
        
        // EUR
        Map<String, Object> eur = new HashMap<>();
        eur.put("code", "EUR");
        eur.put("symbol", "&euro;");
        eur.put("rate", "45,000.0000");
        eur.put("description", "Euro");
        eur.put("rate_float", 45000.0000);
        bpi.put("EUR", eur);
        
        mockData.put("bpi", bpi);
        
        return mockData;
    }
    
    /**
     * Transform Bitcoin price data
     */
    public Map<String, Object> getTransformedData() {
        log.info("Starting Bitcoin price data transformation");
        
        // Get original data
        Map<String, Object> originalData = getOriginalData();
        
        // Create transformed data structure
        Map<String, Object> transformedData = new HashMap<>();
        
        // Format time
        try {
            Map<String, Object> time = (Map<String, Object>) originalData.get("time");
            String updateTimeStr = (String) time.get("updated");
            
            // Format to yyyy/MM/dd HH:mm:ss
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss z");
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            
            LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr, inputFormatter);
            String formattedTime = updateTime.format(outputFormatter);
            
            log.debug("Formatted update time: {}", formattedTime);
            transformedData.put("updateTime", formattedTime);
        } catch (Exception e) {
            log.warn("Error parsing time, using current time instead", e);
            transformedData.put("updateTime", 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        }
        
        // Process currency data
        Map<String, Object> currencies = new LinkedHashMap<>();
        Map<String, Object> bpi = (Map<String, Object>) originalData.get("bpi");
        
        // Process currencies from API response
        bpi.keySet().forEach(code -> {
            Map<String, Object> currency = (Map<String, Object>) bpi.get(code);
            
            Map<String, Object> transformedCurrency = new HashMap<>();
            transformedCurrency.put("code", code);
            
            // Get exchange rate
            double rate = (Double) currency.get("rate_float");
            transformedCurrency.put("rate", rate);
            
            // Get Chinese name
            Currency dbCurrency = currencyRepository.findByCode(code);
            String chineseName = (dbCurrency != null && dbCurrency.getName() != null) 
                    ? dbCurrency.getName() 
                    : code + " (No Chinese name)";
            transformedCurrency.put("chineseName", chineseName);
            
            log.debug("Processing currency: {}, Chinese name: {}, rate: {}", 
                    code, chineseName, rate);
            
            currencies.put(code, transformedCurrency);
        });
        
        // Add currencies that exist in database but not in API response
        addMissingCurrencies(currencies);
        
        transformedData.put("currencies", currencies);
        
        log.info("Completed Bitcoin price data transformation");
        return transformedData;
    }
    
    /**
     * Add currencies that exist in database but not in API response
     */
    private void addMissingCurrencies(Map<String, Object> currencies) {
        // Get all currencies from database
        List<Currency> allCurrencies = currencyRepository.findAll();
        
        // Get USD rate as reference (if exists)
        Double usdRate = null;
        if (currencies.containsKey("USD")) {
            Map<String, Object> usd = (Map<String, Object>) currencies.get("USD");
            usdRate = (Double) usd.get("rate");
        }
        
        // If USD rate doesn't exist, use default value
        if (usdRate == null) {
            usdRate = 50000.0;
        }
        
        // Exchange rate ratios for approximation
        final Map<String, Double> rateRatios = new HashMap<>();
        rateRatios.put("JPY", 0.009); // 1 USD ≈ 111 JPY
        rateRatios.put("CNY", 0.155); // 1 USD ≈ 6.45 CNY
        rateRatios.put("HKD", 0.128); // 1 USD ≈ 7.8 HKD
        rateRatios.put("TWD", 0.036); // 1 USD ≈ 27.8 TWD
        rateRatios.put("AUD", 0.75);  // 1 USD ≈ 1.33 AUD
        rateRatios.put("CAD", 0.80);  // 1 USD ≈ 1.25 CAD
        rateRatios.put("SGD", 0.74);  // 1 USD ≈ 1.35 SGD
        rateRatios.put("CHF", 1.09);  // 1 USD ≈ 0.92 CHF
        
        // For each currency in database
        final Double finalUsdRate = usdRate;
        allCurrencies.forEach(currency -> {
            String code = currency.getCode();
            
            // If the currency is not in API response
            if (!currencies.containsKey(code)) {
                Map<String, Object> estimatedCurrency = new HashMap<>();
                estimatedCurrency.put("code", code);
                estimatedCurrency.put("chineseName", currency.getName());
                
                // Calculate estimated rate
                Double ratio = rateRatios.getOrDefault(code, 0.5); // Default ratio
                Double estimatedRate = finalUsdRate * ratio;
                
                estimatedCurrency.put("rate", estimatedRate);
                estimatedCurrency.put("estimated", true); // Mark as estimated value
                
                currencies.put(code, estimatedCurrency);
                log.debug("Added estimated currency: {}, rate: {}", code, estimatedRate);
            }
        });
    }
} 