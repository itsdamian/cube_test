package com.currency.demo.controller;

import com.currency.demo.model.Currency;
import com.currency.demo.service.CurrencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
public class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CurrencyService currencyService;

    /**
     * Test 1: Query all currency mapping data API
     */
    @Test
    public void testGetAllCurrencies() throws Exception {
        // Execute GET request to retrieve all currencies
        MvcResult result = mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // Print response content
        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content for getting all currencies:");
        System.out.println(responseContent);

        // Verify response content is not empty
        assertNotNull(responseContent);
        assertFalse(responseContent.isEmpty());
    }

    /**
     * Test 2: Add new currency data API
     */
    @Test
    public void testCreateCurrency() throws Exception {
        // Create a new currency object
        String code = "TWD";
        String name = "Taiwan Dollar";
        
        // Check and delete existing currency first
        Currency existingCurrency = currencyService.getCurrencyByCode(code);
        if (existingCurrency != null) {
            currencyService.deleteCurrency(existingCurrency.getId());
        }
        
        Currency newCurrency = new Currency(code, name);

        // Execute POST request to add new currency
        MvcResult result = mockMvc.perform(post("/api/currencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newCurrency)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        // Print response content
        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content for creating a new currency:");
        System.out.println(responseContent);

        // Verify currency was created
        Currency created = currencyService.getCurrencyByCode(code);
        assertNotNull(created);
        assertEquals(name, created.getName());
    }

    /**
     * Test 3: Update currency data API
     */
    @Test
    public void testUpdateCurrency() throws Exception {
        // Check and delete existing currency first
        String code = "KRW";
        Currency existingCurrency = currencyService.getCurrencyByCode(code);
        if (existingCurrency != null) {
            currencyService.deleteCurrency(existingCurrency.getId());
        }
        
        // Create a new currency
        Currency currency = currencyService.createCurrency(new Currency(code, "Korean Won (initial)"));

        // Update currency name
        currency.setName("Korean Won");

        // Execute PUT request to update currency
        MvcResult result = mockMvc.perform(put("/api/currencies/{id}", currency.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(currency)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Korean Won"))
                .andReturn();

        // Print response content
        String responseContent = result.getResponse().getContentAsString();
        System.out.println("Response content for updating a currency:");
        System.out.println(responseContent);

        // Verify currency was updated
        Currency updated = currencyService.getCurrencyByCode(code);
        assertNotNull(updated);
        assertEquals("Korean Won", updated.getName());
    }

    /**
     * Test 4: Delete currency data API
     */
    @Test
    public void testDeleteCurrency() throws Exception {
        // Check and delete existing currency first
        String code = "MYR";
        Currency existingCurrency = currencyService.getCurrencyByCode(code);
        if (existingCurrency != null) {
            currencyService.deleteCurrency(existingCurrency.getId());
        }
        
        // Create a new currency
        Currency currency = currencyService.createCurrency(new Currency(code, "Malaysian Ringgit"));

        // Execute DELETE request to delete currency
        mockMvc.perform(delete("/api/currencies/{id}", currency.getId()))
                .andExpect(status().isOk());

        // Verify currency was deleted
        assertNull(currencyService.getCurrencyByCode(code));
        
        System.out.println("Currency deletion successful, currency code: " + code + " has been deleted");
    }
} 