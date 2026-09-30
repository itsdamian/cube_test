package com.currency.demo.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/prices/converted: the latest BTC-USD price expressed in every configured currency.
 *
 * @param rateSource attribution required by the rate provider's terms
 */
public record ConvertedPrices(String pair, BigDecimal usdPrice, String priceSource, Instant priceEventTime,
                              String rateSource, List<Item> items) {

    /**
     * @param price         BTC price in this currency; {@code null} if no rate is known for the code
     * @param rate          units of this currency per 1 USD; {@code null} if unknown
     * @param rateUpdatedAt when the provider last updated this rate ("匯率更新時間")
     */
    public record Item(long currencyId, String code, String name, BigDecimal price, BigDecimal rate,
                       Instant rateUpdatedAt) {
    }
}
