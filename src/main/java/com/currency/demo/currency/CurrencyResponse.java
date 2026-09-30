package com.currency.demo.currency;

import java.time.Instant;

/** What the API returns for a currency; decouples the JSON contract from the JPA entity. */
public record CurrencyResponse(Long id, String code, String name, Instant createdAt, Instant updatedAt) {

    static CurrencyResponse from(Currency c) {
        return new CurrencyResponse(c.getId(), c.getCode(), c.getName(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
