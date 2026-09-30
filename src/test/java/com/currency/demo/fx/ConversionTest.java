package com.currency.demo.fx;

import com.currency.demo.pricing.PriceTick;
import com.currency.demo.pricing.PriceTickRepository;
import com.currency.demo.support.IntegrationTest;
import com.currency.demo.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh + conversion against PostgreSQL. The HTTP source is a Mockito stub (the real one is
 * never called). {@code @Transactional}: rates and the extra currency are rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversionTest extends IntegrationTest {

    static final Instant PROVIDER_TIME = Instant.parse("2026-09-30T00:02:31Z");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    FxRateRepository repository;

    @Autowired
    PriceTickRepository ticks;

    private FxRateRefresher refresherReturning(ExchangeRateClient client) {
        return new FxRateRefresher(client, repository, new MutableClock(PROVIDER_TIME.plusSeconds(60)));
    }

    @Test
    void failedRefreshKeepsThePreviousRates() {
        ExchangeRateClient client = mock(ExchangeRateClient.class);
        when(client.fetch())
                .thenReturn(new FxSnapshot(PROVIDER_TIME, Map.of("USD", BigDecimal.ONE, "TWD", new BigDecimal("31.84071"))))
                .thenThrow(new FxUnavailableException("429 Too Many Requests", null));
        FxRateRefresher refresher = refresherReturning(client);

        assertThat(refresher.refresh()).isTrue();
        assertThat(refresher.refresh()).as("429").isFalse();

        FxRateRepository.Rate twd = repository.findAll().get("TWD");
        assertThat(twd.ratePerUsd()).isEqualByComparingTo("31.84071");
        assertThat(twd.providerUpdatedAt()).isEqualTo(PROVIDER_TIME);
    }

    @Test
    void convertedPricesUseTheLatestPriceAndStoredRatesWithUpdateTime() throws Exception {
        ExchangeRateClient client = mock(ExchangeRateClient.class);
        when(client.fetch()).thenReturn(new FxSnapshot(PROVIDER_TIME, Map.of(
                "USD", BigDecimal.ONE, "EUR", new BigDecimal("0.88152"), "GBP", new BigDecimal("0.755961"),
                "TWD", new BigDecimal("31.84071"), "JPY", new BigDecimal("157.389062"))));
        refresherReturning(client).refresh();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/currencies")
                .contentType("application/json").content("{\"code\":\"XAU\",\"name\":\"黃金\"}"))
                .andExpect(status().isCreated());

        // Our own tick, far in the future so it is the latest one; rolled back with the test transaction.
        Instant future = Instant.parse("2200-01-01T00:00:00Z");
        BigDecimal usd = new BigDecimal("84045.50");
        ticks.insertAll(List.of(new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, usd, "coinbase", future, future)));
        JsonNode converted = json.readTree(mvc.perform(get("/api/prices/converted")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(converted.get("usdPrice").decimalValue()).isEqualByComparingTo(usd);
        assertThat(converted.get("rateSource").asText()).contains("Exchange Rate API");
        Map<String, BigDecimal> expectedRates = Map.of("USD", BigDecimal.ONE, "EUR", new BigDecimal("0.88152"),
                "GBP", new BigDecimal("0.755961"), "TWD", new BigDecimal("31.84071"), "JPY", new BigDecimal("157.389062"));
        int checked = 0;
        for (JsonNode item : converted.get("items")) {
            String code = item.get("code").asText();
            if (code.equals("XAU")) {
                assertThat(item.get("price").isNull()).as("no rate known").isTrue();
                assertThat(item.get("rate").isNull()).isTrue();
                assertThat(item.get("name").asText()).isEqualTo("黃金");
                continue;
            }
            BigDecimal rate = expectedRates.get(code);
            BigDecimal price = item.get("price").decimalValue();
            // AC3 asks for < 0.5 % against the source's rate; we are exact to 8 decimals.
            BigDecimal relativeError = price.divide(usd.multiply(rate), MathContext.DECIMAL64)
                    .subtract(BigDecimal.ONE).abs();
            assertThat(relativeError).as(code).isLessThan(new BigDecimal("0.0001"));
            assertThat(item.get("rate").decimalValue()).isEqualByComparingTo(rate);
            assertThat(Instant.parse(item.get("rateUpdatedAt").asText())).isEqualTo(PROVIDER_TIME);
            checked++;
        }
        assertThat(checked).isEqualTo(5);
    }
}
