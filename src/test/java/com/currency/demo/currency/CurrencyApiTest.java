package com.currency.demo.currency;

import com.currency.demo.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Currency REST API against a real PostgreSQL (Testcontainers) with the Flyway schema.
 *
 * <p>{@code @Transactional} on a MockMvc test rolls back after each test (MockMvc runs
 * the controller on the test thread), so tests never leak rows into each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrencyApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CurrencyRepository repository;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        mvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CHF\",\"name\":\"瑞士法郎\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/currencies/"
                        + repository.findByCode("CHF").orElseThrow().getId())))
                .andExpect(jsonPath("$.code").value("CHF"))
                .andExpect(jsonPath("$.name").value("瑞士法郎"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getByIdAndList() throws Exception {
        long id = repository.findByCode("TWD").orElseThrow().getId();

        mvc.perform(get("/api/currencies/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TWD"))
                .andExpect(jsonPath("$.name").value("新台幣"));

        mvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'TWD')].name").value("新台幣"));
    }

    @Test
    void updateChangesCodeAndName() throws Exception {
        long id = repository.save(new Currency("HKD", "港幣")).getId();

        mvc.perform(put("/api/currencies/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HKD\",\"name\":\"港元\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("港元"));
    }

    @Test
    void deleteReturns204ThenGetReturns404() throws Exception {
        long id = repository.save(new Currency("SGD", "新加坡幣")).getId();

        mvc.perform(delete("/api/currencies/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/currencies/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Currency " + id + " not found"));
    }

    @Test
    void unknownIdIs404ForGetUpdateAndDelete() throws Exception {
        mvc.perform(get("/api/currencies/{id}", 999_999)).andExpect(status().isNotFound());
        mvc.perform(put("/api/currencies/{id}", 999_999).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AAA\",\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/currencies/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateCodeIs409OnCreateAndUpdate() throws Exception {
        mvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"USD\",\"name\":\"美元\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Currency code USD already exists"));

        long jpyId = repository.findByCode("JPY").orElseThrow().getId();
        mvc.perform(put("/api/currencies/{id}", jpyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"USD\",\"name\":\"日圓\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidBodyIs400ProblemDetail() throws Exception {
        for (String body : new String[]{
                "{\"code\":\"usd\",\"name\":\"x\"}",    // lowercase
                "{\"code\":\"US\",\"name\":\"x\"}",     // too short
                "{\"code\":\"USDT\",\"name\":\"x\"}",   // too long
                "{\"code\":\"AUD\",\"name\":\"\"}",     // blank name
                "{\"code\":\"AUD\"}"}) {                 // missing name
            mvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }
}
