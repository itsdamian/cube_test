package com.currency.demo.service;

import com.currency.demo.model.Currency;
import com.currency.demo.repository.CurrencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CoindeskServiceTest {

    @Autowired
    private CoindeskService coindeskService;

    @Autowired
    private CurrencyRepository currencyRepository;

    @MockBean
    private RestTemplate restTemplate;
    
    private Map<String, Object> mockBitcoinData;
    
    @BeforeEach
    public void setup() {
        // Create mock data
        mockBitcoinData = new HashMap<>();
        
        // Time information
        Map<String, Object> time = new HashMap<>();
        time.put("updated", "Mar 29, 2025 11:53:00 UTC");
        time.put("updatedISO", "2025-03-29T11:53:00+00:00");
        time.put("updateduk", "Mar 29, 2025 at 11:53 BST");
        mockBitcoinData.put("time", time);
        
        // Disclaimer
        mockBitcoinData.put("disclaimer", "This data was produced from the CoinDesk Bitcoin Price Index (USD).");
        mockBitcoinData.put("chartName", "Bitcoin");
        
        // Bitcoin price index
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
        
        mockBitcoinData.put("bpi", bpi);
        
        // Set mock behavior for RestTemplate
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockBitcoinData);
        
        // Ensure currency data exists
        ensureCurrencyExists("USD", "US Dollar");
        ensureCurrencyExists("EUR", "Euro");
        ensureCurrencyExists("GBP", "British Pound");
    }

    /**
     * Test for getting original Coindesk API data
     */
    @Test
    public void testGetOriginalData() {
        // Get original data
        Map<String, Object> originalData = coindeskService.getOriginalData();
        
        // Print data content
        System.out.println("Original Coindesk API data:");
        System.out.println(originalData);
        
        // Verify data structure
        assertNotNull(originalData);
        assertTrue(originalData.containsKey("time"), "Should contain time field");
        assertTrue(originalData.containsKey("disclaimer"), "Should contain disclaimer field");
        assertTrue(originalData.containsKey("bpi"), "Should contain bpi field");
        
        // Verify bpi structure
        Map<String, Object> bpi = (Map<String, Object>) originalData.get("bpi");
        assertNotNull(bpi);
        assertTrue(bpi.containsKey("USD"), "bpi should contain USD");
        assertTrue(bpi.containsKey("GBP"), "bpi should contain GBP");
        assertTrue(bpi.containsKey("EUR"), "bpi should contain EUR");
    }

    /**
     * Test for getting transformed Bitcoin price data
     */
    @Test
    public void testGetTransformedData() {
        // Get transformed data
        Map<String, Object> transformedData = coindeskService.getTransformedData();
        
        // Print data content
        System.out.println("Transformed data:");
        System.out.println(transformedData);
        
        // Verify data structure
        assertNotNull(transformedData);
        assertTrue(transformedData.containsKey("updateTime"), "Should contain updateTime field");
        assertTrue(transformedData.containsKey("currencies"), "Should contain currencies field");
        
        // Verify currencies structure
        Map<String, Object> currencies = (Map<String, Object>) transformedData.get("currencies");
        assertNotNull(currencies);
        assertTrue(currencies.containsKey("USD"), "currencies should contain USD");
        
        // Verify currency fields
        Map<String, Object> usd = (Map<String, Object>) currencies.get("USD");
        assertNotNull(usd);
        assertEquals("USD", usd.get("code"));
        assertNotNull(usd.get("chineseName"));
        assertNotNull(usd.get("rate"));
        assertTrue((Double) usd.get("rate") > 0, "Rate should be greater than 0");
    }
    
    /**
     * Ensure currency exists
     */
    private void ensureCurrencyExists(String code, String name) {
        Currency currency = currencyRepository.findByCode(code);
        if (currency == null) {
            currency = new Currency(code, name);
            currencyRepository.save(currency);
        } else if (!currency.getName().equals(name)) {
            // If exists but name is different, update the name
            currency.setName(name);
            currencyRepository.save(currency);
        }
    }
} 