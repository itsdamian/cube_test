package com.currency.demo.web;

import com.currency.demo.candle.Candle;
import com.currency.demo.candle.CandleController;
import com.currency.demo.candle.CandleRepository;
import com.currency.demo.feed.FeedStatus;
import com.currency.demo.fx.ConversionController;
import com.currency.demo.fx.ConversionService;
import com.currency.demo.fx.ConvertedPrices;
import com.currency.demo.pricing.PriceController;
import com.currency.demo.pricing.PriceDtos.HistoryPage;
import com.currency.demo.pricing.PriceDtos.LatestPrice;
import com.currency.demo.pricing.PriceDtos.PricePoint;
import com.currency.demo.pricing.PriceDtos.Trend;
import com.currency.demo.pricing.PriceDtos.TrendPoint;
import com.currency.demo.pricing.PriceQueryService;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Frontend/backend contract (QA T3): the JSON the real controllers produce must match the
 * samples in {@code contracts/api-samples/}, which the frontend's MSW mocks load verbatim.
 * Change a DTO -> this test fails until the sample is updated, so the two sides cannot drift.
 *
 * <p>Regenerate after an intentional change: {@code ./mvnw test -Dtest=ContractSamplesTest -Dcontracts.update=true}.
 * The services are mocked with fixed data, so the output is deterministic.
 */
@WebMvcTest({PriceController.class, CandleController.class, ConversionController.class})
@Import(ContractSamplesTest.FixedClock.class)
class ContractSamplesTest {

    static final Path SAMPLES = Path.of("contracts", "api-samples");
    static final Instant T = Instant.parse("2026-09-29T08:00:00.123456Z");

    static class FixedClock {
        @org.springframework.context.annotation.Bean
        java.time.Clock clock() {
            return java.time.Clock.fixed(Instant.parse("2026-09-29T08:15:00Z"), java.time.ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PriceQueryService prices;

    @MockitoBean
    CandleRepository candles;

    @MockitoBean
    ConversionService conversion;

    @Test
    void pricesConverted() throws Exception {
        Instant rates = Instant.parse("2026-09-30T00:02:31Z");
        when(conversion.converted()).thenReturn(new ConvertedPrices("BTC-USD", new BigDecimal("84045.50"), "coinbase", T,
                "Rates By Exchange Rate API (https://www.exchangerate-api.com)", List.of(
                new ConvertedPrices.Item(1, "EUR", "歐元", new BigDecimal("74087.78716000"), new BigDecimal("0.88152"), rates),
                new ConvertedPrices.Item(4, "TWD", "新台幣", new BigDecimal("2676068.51569500"), new BigDecimal("31.84071"), rates),
                new ConvertedPrices.Item(9, "XAU", "黃金", null, null, null))));
        assertMatchesSample("/api/prices/converted", "prices-converted.json");
    }

    @Test
    void candles() throws Exception {
        Instant open = Instant.parse("2026-09-29T08:00:00Z");
        when(candles.find(any(), any(), any(), any())).thenReturn(List.of(
                new Candle("BTC-USD", "1m", open, open.plusSeconds(60), new BigDecimal("84040.10"),
                        new BigDecimal("84061.00"), new BigDecimal("84031.55"), new BigDecimal("84052.3"), 57),
                new Candle("BTC-USD", "1m", open.plusSeconds(60), open.plusSeconds(120), new BigDecimal("84052.3"),
                        new BigDecimal("84070.0"), new BigDecimal("84049.9"), new BigDecimal("84066.12"), 61)));
        assertMatchesSample("/api/candles?interval=1m&from=2026-09-29T08:00:00Z&to=2026-09-29T08:02:00Z",
                "candles.json");
    }

    @Test
    void pricesLatest() throws Exception {
        when(prices.latest()).thenReturn(new LatestPrice("BTC-USD", new BigDecimal("84045.50"), "coinbase", T,
                new FeedStatus("coinbase", FeedStatus.State.LIVE, T, T.plusMillis(500))));
        assertMatchesSample("/api/prices/latest", "prices-latest.json");
    }

    @Test
    void pricesHistory() throws Exception {
        when(prices.history(any(), any(), anyInt(), isNull())).thenReturn(new HistoryPage(List.of(
                new PricePoint(T, new BigDecimal("84045.5"), "coinbase", T.plusMillis(80),
                        java.util.UUID.fromString("3f1c2a4e-5b6d-3e7f-8a9b-0c1d2e3f4a5b")),
                new PricePoint(T.plusMillis(250), new BigDecimal("84046.0"), "coinbase", T.plusMillis(330),
                        java.util.UUID.fromString("9a8b7c6d-5e4f-3a2b-9c0d-1e2f3a4b5c6d"))),
                "MTc5MDY2NjgwMDoxMjM0NTYwMDA6NDI"));
        assertMatchesSample("/api/prices/history?from=2026-09-29T08:00:00Z&to=2026-09-29T08:15:00Z&limit=2",
                "prices-history.json");
    }

    @Test
    void pricesTrend() throws Exception {
        Instant from = Instant.parse("2026-09-29T08:00:00Z");
        when(prices.trend(any(), any(), anyInt())).thenReturn(new Trend(from, from.plusSeconds(900), 3, List.of(
                new TrendPoint(from, from.plusMillis(2_900), new BigDecimal("84040.1")),
                new TrendPoint(from.plusSeconds(3), from.plusMillis(5_100), new BigDecimal("84041.7")))));
        assertMatchesSample("/api/prices/trend?from=2026-09-29T08:00:00Z&to=2026-09-29T08:15:00Z&points=300",
                "prices-trend.json");
    }

    void assertMatchesSample(String url, String sample) throws Exception {
        String actual = mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path file = SAMPLES.resolve(sample);
        if (Boolean.getBoolean("contracts.update") || !Files.exists(file)) {
            write(file, actual);
        }
        assertThat(file).as("contract sample %s", file).exists();
        JSONAssert.assertEquals("contract drift in " + file, Files.readString(file), actual, JSONCompareMode.STRICT);
    }

    private static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        // Keep numbers exactly as the API wrote them (84045.50 must not become 84045.5).
        var exact = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();
        String pretty = exact.readTree(json).toPrettyString();
        Files.writeString(file, pretty + System.lineSeparator());
    }
}
