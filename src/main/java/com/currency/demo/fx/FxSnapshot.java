package com.currency.demo.fx;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One exchange-rate download.
 *
 * @param providerUpdatedAt when the provider last updated its rates (shown to users as "匯率更新時間")
 * @param ratesPerUsd       units of each currency per 1 USD, e.g. TWD -> 31.84071
 */
public record FxSnapshot(Instant providerUpdatedAt, Map<String, BigDecimal> ratesPerUsd) {

    public FxSnapshot {
        ratesPerUsd = Map.copyOf(ratesPerUsd);
    }
}
