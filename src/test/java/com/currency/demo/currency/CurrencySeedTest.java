package com.currency.demo.currency;

import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC10: a brand-new database already contains USD/EUR/GBP/TWD/JPY with Chinese names,
 * created by the Flyway migration V2__seed_currencies.sql.
 *
 * <p>The shared test database is only ever changed by tests that roll back
 * ({@code @Transactional}), so it still looks "brand new" here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CurrencySeedTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void freshDatabaseHasExactlyTheFiveDefaultCurrencies() throws Exception {
        mvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                // list is ordered by code
                .andExpect(jsonPath("$[*].code", contains("EUR", "GBP", "JPY", "TWD", "USD")))
                .andExpect(jsonPath("$[*].name", contains("歐元", "英鎊", "日圓", "新台幣", "美元")));
    }
}
