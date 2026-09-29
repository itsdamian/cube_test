package com.currency.demo.feed;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * Exchange-specific protocol: what to send to subscribe, and how to read what comes back.
 *
 * <p>Implementations are pure functions of their input (no I/O, no clock), which is why
 * they can be tested exhaustively with captured messages.
 */
public interface FeedMessageParser {

    /**
     * Parser-owned JSON mapper. {@code USE_BIG_DECIMAL_FOR_FLOATS} keeps a JSON number such as
     * {@code 84034.9} as the exact decimal text instead of going through {@code double};
     * disabling {@code STRIP_TRAILING_BIGDECIMAL_ZEROES} stops the tree model from turning
     * {@code 84035.0} into {@code 84035}, so the price keeps the exchange's exact digits.
     */
    ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    /** Source name written into every tick, e.g. {@code coinbase}. */
    String sourceName();

    /** The text frame to send right after connecting. */
    String subscribeMessage();

    /**
     * Parse one complete text message.
     *
     * @param text       the raw message
     * @param receivedAt when our app received it (becomes {@code PriceTick.receivedAt})
     * @return never {@code null}; malformed input yields {@link FeedMessage.Invalid}
     */
    FeedMessage parse(String text, Instant receivedAt);
}
