package com.currency.demo.pricing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque keyset cursor = position of the last row returned: (event_time, id).
 * Encoded as URL-safe Base64 so clients treat it as a token, not something to build by hand.
 */
record HistoryCursor(Instant eventTime, long id) {

    String encode() {
        String raw = eventTime.getEpochSecond() + ":" + eventTime.getNano() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static HistoryCursor decode(String token) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8).split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("wrong number of parts");
            }
            return new HistoryCursor(Instant.ofEpochSecond(Long.parseLong(parts[0]), Long.parseLong(parts[1])),
                    Long.parseLong(parts[2]));
        } catch (RuntimeException e) {
            throw new InvalidQueryException("Invalid cursor");
        }
    }
}
