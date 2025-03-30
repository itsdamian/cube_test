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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Map<String, Object> mockIncompleteData;
    private Map<String, Object> mockEmptyData;
    
    @BeforeEach
    public void setup() {
        // Create complete mock data
        setupCompleteMockData();
        
        // Create incomplete mock data (missing some fields)
        setupIncompleteMockData();
        
        // Create empty mock data
        mockEmptyData = new HashMap<>();
        
        // Default RestTemplate mock behavior - return complete data
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockBitcoinData);
        
        // Setup test currencies
        setupTestCurrencies();
    }
    
    private void setupCompleteMockData() {
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
    }
    
    private void setupIncompleteMockData() {
        mockIncompleteData = new HashMap<>();
        
        // Only include time information, no bpi
        Map<String, Object> time = new HashMap<>();
        time.put("updated", "Mar 29, 2025 11:53:00 UTC");
        time.put("updatedISO", "2025-03-29T11:53:00+00:00");
        mockIncompleteData.put("time", time);
        
        // Add empty bpi
        mockIncompleteData.put("bpi", new HashMap<>());
    }
    
    private void setupTestCurrencies() {
        // Ensure basic currency data exists
        ensureCurrencyExists("USD", "美金");
        ensureCurrencyExists("EUR", "歐元");
        ensureCurrencyExists("GBP", "英鎊");
        
        // Add other common currencies
        ensureCurrencyExists("JPY", "日圓");
        ensureCurrencyExists("CNY", "人民幣");
        ensureCurrencyExists("HKD", "港幣");
        ensureCurrencyExists("TWD", "台幣");
    }

    /**
     * Test for getting original Coindesk API data with complete data
     */
    @Test
    public void testGetOriginalData() {
        // Set RestTemplate to return complete data
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockBitcoinData);
        
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
     * Test for handling API failure
     */
    @Test
    public void testGetOriginalDataWithApiFailure() {
        // Mock API call failure
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenThrow(new RuntimeException("API unreachable"));
        
        // Get original data (should return default data even if API fails)
        Map<String, Object> originalData = coindeskService.getOriginalData();
        
        // Verify default data is returned
        assertNotNull(originalData);
        assertTrue(originalData.containsKey("bpi"), "Should contain bpi field even with API failure");
        
        // Verify bpi contains at least USD
        Map<String, Object> bpi = (Map<String, Object>) originalData.get("bpi");
        assertTrue(bpi.containsKey("USD"), "Default data should contain USD");
    }
    
    /**
     * Test for handling incomplete API response
     */
    @Test
    public void testGetOriginalDataWithIncompleteResponse() {
        // Mock API returning incomplete data
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockIncompleteData);
        
        // Get original data (should return default data)
        Map<String, Object> originalData = coindeskService.getOriginalData();
        
        // Verify default data is returned
        assertNotNull(originalData);
        assertTrue(originalData.containsKey("bpi"), "Should contain bpi field");
        
        // Verify bpi contains at least USD
        Map<String, Object> bpi = (Map<String, Object>) originalData.get("bpi");
        assertTrue(bpi.containsKey("USD"), "Default data should contain USD");
    }

    /**
     * Test for getting transformed Bitcoin price data
     */
    @Test
    public void testGetTransformedData() {
        // Set RestTemplate to return complete data
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockBitcoinData);
        
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
     * Test for adding missing currencies
     */
    @Test
    public void testAddMissingCurrencies() {
        // Create a mock data containing only USD
        Map<String, Object> usdOnlyData = new HashMap<>();
        
        // Time information
        Map<String, Object> time = new HashMap<>();
        time.put("updated", "Mar 29, 2025 11:53:00 UTC");
        time.put("updatedISO", "2025-03-29T11:53:00+00:00");
        usdOnlyData.put("time", time);
        
        // Add only USD
        Map<String, Object> bpi = new HashMap<>();
        Map<String, Object> usd = new HashMap<>();
        usd.put("code", "USD");
        usd.put("rate", "50,000.0000");
        usd.put("rate_float", 50000.0000);
        bpi.put("USD", usd);
        usdOnlyData.put("bpi", bpi);
        
        // Set RestTemplate to return data with only USD
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(usdOnlyData);
        
        // Get transformed data
        Map<String, Object> transformedData = coindeskService.getTransformedData();
        
        // Verify additional currencies are added
        Map<String, Object> currencies = (Map<String, Object>) transformedData.get("currencies");
        
        // Check if at least one additional currency is added
        boolean hasExtraCurrency = false;
        for (String code : new String[]{"JPY", "CNY", "HKD", "TWD"}) {
            if (currencies.containsKey(code)) {
                hasExtraCurrency = true;
                
                // Verify the added currency data format is correct
                Map<String, Object> currency = (Map<String, Object>) currencies.get(code);
                assertEquals(code, currency.get("code"));
                assertNotNull(currency.get("chineseName"));
                assertNotNull(currency.get("rate"));
                assertTrue((Double) currency.get("rate") > 0, "Rate should be greater than 0");
                
                // Verify marked as estimated
                assertTrue((Boolean) currency.getOrDefault("estimated", false), 
                           "Extra currencies should be marked as estimated");
            }
        }
        
        assertTrue(hasExtraCurrency, "Should add at least one extra currency");
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