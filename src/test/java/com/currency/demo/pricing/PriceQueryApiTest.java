package com.currency.demo.pricing;

import com.currency.demo.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/prices against PostgreSQL. Data lives in 2031 so it never overlaps other tests' ticks
 * in the shared test database. 12,000 ticks in 10 minutes; every pair of ticks shares one
 * event_time, so paging must also be exact on ties (keyset on event_time AND id).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PriceQueryApiTest extends IntegrationTest {

    static final Instant FROM = Instant.parse("2031-01-01T00:00:00Z");
    static final Instant TO = Instant.parse("2031-01-01T00:10:00Z");
    static final int COUNT = 12_000;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PriceTickRepository ticks;

    final List<PriceTick> inserted = new ArrayList<>();

    @BeforeAll
    void insertTenMinutesOfTicks() {
        for (int i = 0; i < COUNT; i++) {
            Instant t = FROM.plusMillis(100L * (i / 2));    // 2 ticks per 100 ms -> 600 s
            inserted.add(new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD,
                    new BigDecimal("80000.00000000").add(BigDecimal.valueOf(i)), "coinbase", t, t.plusMillis(i % 2)));
        }
        ticks.insertAll(inserted);
    }

    @Test
    void pagingThroughTwelveThousandTicksReturnsEachExactlyOnce() throws Exception {
        List<BigDecimal> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var req = get("/api/prices/history").param("from", FROM.toString()).param("to", TO.toString())
                    .param("limit", "5000");
            if (cursor != null) {
                req.param("cursor", cursor);
            }
            JsonNode page = json.readTree(mvc.perform(req).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            page.get("items").forEach(item -> seen.add(item.get("price").decimalValue()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(3);                       // 5000 + 5000 + 2000
        assertThat(seen).hasSize(COUNT);
        Set<BigDecimal> distinct = new HashSet<>();
        seen.forEach(p -> distinct.add(p.stripTrailingZeros()));
        assertThat(distinct).as("no duplicates").hasSize(COUNT);
    }

    @Test
    void defaultPageSizeIs1000AndRangeIsHalfOpen() throws Exception {
        mvc.perform(get("/api/prices/history").param("from", FROM.toString()).param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1000))
                .andExpect(jsonPath("$.nextCursor").isString());

        // [from, to): the two ticks exactly at FROM are included, none at TO exists.
        mvc.perform(get("/api/prices/history").param("from", FROM.toString())
                        .param("to", FROM.plusMillis(100).toString()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void invalidParametersAre400() throws Exception {
        mvc.perform(get("/api/prices/history").param("from", TO.toString()).param("to", FROM.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("from must be before to"));
        mvc.perform(get("/api/prices/history").param("from", FROM.toString()).param("to", TO.toString())
                .param("limit", "5001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/prices/history").param("from", FROM.toString()).param("to", TO.toString())
                .param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/prices/history").param("from", FROM.toString()).param("to", TO.toString())
                .param("cursor", "not-a-cursor")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/prices/history").param("from", "yesterday")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/prices/trend").param("from", FROM.toString()).param("to", TO.toString())
                .param("points", "1001")).andExpect(status().isBadRequest());
    }

    @Test
    void trendReturnsAtMostPointsAndEachIsTheLastPriceOfItsBucket() throws Exception {
        JsonNode trend = json.readTree(mvc.perform(get("/api/prices/trend")
                        .param("from", FROM.toString()).param("to", TO.toString()).param("points", "300"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(trend.get("bucketSeconds").asLong()).isEqualTo(2);    // 600 s / 300
        JsonNode points = trend.get("points");
        assertThat(points.size()).isLessThanOrEqualTo(300).isEqualTo(300);

        // Expected: per 2-second bucket, the tick with the greatest (event_time, received_at).
        Map<Instant, PriceTick> lastPerBucket = new LinkedHashMap<>();
        for (PriceTick t : inserted) {
            long bucket = (t.eventTime().getEpochSecond() - FROM.getEpochSecond()) / 2;
            lastPerBucket.merge(FROM.plusSeconds(bucket * 2), t, (a, b) ->
                    b.eventTime().compareTo(a.eventTime()) > 0
                            || (b.eventTime().equals(a.eventTime()) && b.receivedAt().isAfter(a.receivedAt())) ? b : a);
        }
        for (JsonNode p : points) {
            PriceTick expected = lastPerBucket.get(Instant.parse(p.get("bucketStart").asText()));
            assertThat(p.get("price").decimalValue()).isEqualByComparingTo(expected.price());
            assertThat(Instant.parse(p.get("eventTime").asText())).isEqualTo(expected.eventTime());
        }
    }

    @Test
    void latestReturnsTheNewestTickByEventTime() throws Exception {
        Instant far = Instant.parse("2100-01-01T00:00:00Z");
        ticks.insertAll(List.of(new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD,
                new BigDecimal("123456.78900000"), "kraken", far, far)));

        mvc.perform(get("/api/prices/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair").value("BTC-USD"))
                .andExpect(jsonPath("$.price").value(123456.789))
                .andExpect(jsonPath("$.source").value("kraken"))
                .andExpect(jsonPath("$.eventTime").value("2100-01-01T00:00:00Z"));
    }
}
