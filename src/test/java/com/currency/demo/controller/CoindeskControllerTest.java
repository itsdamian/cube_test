package com.currency.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CoindeskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Test 5: Call Coindesk API and display its content
     */
    @Test
    public void testGetOriginalPrice() throws Exception {
        // Execute GET request to get original Coindesk API data
        MvcResult result = mockMvc.perform(get("/api/bitcoin/price/original"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Get response content
        String responseContent = result.getResponse().getContentAsString();
        
        // Print response content
        System.out.println("Original Coindesk API data:");
        System.out.println(responseContent);

        // Parse JSON response
        JsonNode rootNode = objectMapper.readTree(responseContent);
        
        // Verify response contains required fields
        assertTrue(rootNode.has("time"), "Response should include time field");
        assertTrue(rootNode.has("disclaimer"), "Response should include disclaimer field");
        assertTrue(rootNode.has("chartName"), "Response should include chartName field");
        assertTrue(rootNode.has("bpi"), "Response should include bpi field");
        
        // Verify bpi contains currency information
        JsonNode bpi = rootNode.get("bpi");
        assertTrue(bpi.has("USD"), "bpi should include USD");
        assertTrue(bpi.has("GBP"), "bpi should include GBP");
        assertTrue(bpi.has("EUR"), "bpi should include EUR");
    }

    /**
     * Test 6: Call data transformation API and display its content
     */
    @Test
    public void testGetTransformedPrice() throws Exception {
        // Execute GET request to get transformed data
        MvcResult result = mockMvc.perform(get("/api/bitcoin/price"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Get response content
        String responseContent = result.getResponse().getContentAsString();
        
        // Print response content
        System.out.println("Transformed data:");
        System.out.println(responseContent);

        // Parse JSON response
        JsonNode rootNode = objectMapper.readTree(responseContent);
        
        // Verify response contains required fields
        assertTrue(rootNode.has("updateTime"), "Response should include updateTime field");
        assertTrue(rootNode.has("currencies"), "Response should include currencies field");
        
        // Verify currencies contains currency information
        JsonNode currencies = rootNode.get("currencies");
        assertTrue(currencies.has("USD"), "currencies should include USD");
        
        // Verify currency object structure
        if (currencies.has("USD")) {
            JsonNode usd = currencies.get("USD");
            assertTrue(usd.has("code"), "USD should include code field");
            assertTrue(usd.has("chineseName"), "USD should include chineseName field");
            assertTrue(usd.has("rate"), "USD should include rate field");
            
            // Verify Chinese name
            String chineseName = usd.get("chineseName").asText();
            assertNotNull(chineseName);
            assertFalse(chineseName.isEmpty());
            
            // Verify exchange rate
            double rate = usd.get("rate").asDouble();
            assertTrue(rate > 0, "Exchange rate should be greater than 0");
        }
    }
} 