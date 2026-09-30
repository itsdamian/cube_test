package com.currency.demo.fx;

import com.currency.demo.currency.CurrencyResponse;
import com.currency.demo.currency.CurrencyService;
import com.currency.demo.pricing.NoPriceYetException;
import com.currency.demo.pricing.PriceQueryRepository;
import com.currency.demo.pricing.PriceTick;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * BTC price in each currency = BTC-USD price x (currency units per USD).
 * Exact decimal arithmetic; the result keeps 8 decimals and the frontend rounds for display.
 */
@Service
public class ConversionService {

    static final String RATE_SOURCE = "Rates By Exchange Rate API (https://www.exchangerate-api.com)";

    private final PriceQueryRepository prices;
    private final CurrencyService currencies;
    private final FxRateRepository rates;

    public ConversionService(PriceQueryRepository prices, CurrencyService currencies, FxRateRepository rates) {
        this.prices = prices;
        this.currencies = currencies;
        this.rates = rates;
    }

    public ConvertedPrices converted() {
        PriceQueryRepository.Row latest = prices.latest(PriceTick.BTC_USD).orElseThrow(NoPriceYetException::new);
        Map<String, FxRateRepository.Rate> known = rates.findAll();
        List<CurrencyResponse> all = currencies.findAll();
        List<ConvertedPrices.Item> items = all.stream().map(c -> {
            FxRateRepository.Rate rate = known.get(c.code());
            return rate == null
                    ? new ConvertedPrices.Item(c.id(), c.code(), c.name(), null, null, null)
                    : new ConvertedPrices.Item(c.id(), c.code(), c.name(), convert(latest.price(), rate.ratePerUsd()),
                    rate.ratePerUsd(), rate.providerUpdatedAt());
        }).toList();
        return new ConvertedPrices(PriceTick.BTC_USD, latest.price(), latest.source(), latest.eventTime(),
                RATE_SOURCE, items);
    }

    static BigDecimal convert(BigDecimal usdPrice, BigDecimal ratePerUsd) {
        return usdPrice.multiply(ratePerUsd).setScale(8, RoundingMode.HALF_EVEN);
    }
}
