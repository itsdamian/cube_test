package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Kraken WebSocket v2 ({@code wss://ws.kraken.com/v2}), channel {@code trade}.
 *
 * <p>One {@code update} message may contain several trades - each becomes its own tick.
 * We subscribe with {@code snapshot:false}; if a {@code snapshot} (recent historical trades)
 * arrives anyway it is ignored, because old event times would arrive "late" for the candle
 * windows. Kraken sends a {@code heartbeat} about once per second while subscribed.
 * Prices are JSON numbers, read as exact decimals (see {@link FeedMessageParser#JSON}).
 */
public final class KrakenMessageParser implements FeedMessageParser {

    public static final String SOURCE = "kraken";
    private static final String SYMBOL = "BTC/USD";
    private static final Logger log = LoggerFactory.getLogger(KrakenMessageParser.class);

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public String subscribeMessage() {
        return """
                {"method":"subscribe","params":{"channel":"trade","symbol":["BTC/USD"],"snapshot":false}}""";
    }

    @Override
    public FeedMessage parse(String text, Instant receivedAt) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JsonProcessingException e) {
            return new FeedMessage.Invalid("not JSON: " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            return new FeedMessage.Invalid("not a JSON object");
        }
        if (root.has("method")) {
            return root.path("success").asBoolean(false)
                    ? new FeedMessage.Control(root.path("method").asText() + " ok")
                    : new FeedMessage.ExchangeError(root.path("error").asText("unknown error"));
        }
        String channel = root.path("channel").asText("");
        return switch (channel) {
            case "heartbeat" -> new FeedMessage.Heartbeat();
            case "status" -> new FeedMessage.Control("status");
            case "trade" -> trades(root, receivedAt);
            default -> new FeedMessage.Invalid("unknown channel '" + channel + "'");
        };
    }

    private FeedMessage trades(JsonNode root, Instant receivedAt) {
        if (!"update".equals(root.path("type").asText())) {
            return new FeedMessage.Control("trade " + root.path("type").asText() + " ignored");
        }
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return new FeedMessage.Invalid("trade update without data");
        }
        List<PriceTick> ticks = new ArrayList<>(data.size());
        for (JsonNode trade : data) {
            if (!SYMBOL.equals(trade.path("symbol").asText())) {
                continue;
            }
            try {
                JsonNode priceNode = trade.path("price");
                if (!priceNode.isNumber()) {
                    throw new NumberFormatException("price is not a number: " + priceNode);
                }
                BigDecimal price = priceNode.decimalValue();
                Instant time = Instant.parse(trade.path("timestamp").asText());
                if (price.signum() <= 0) {
                    throw new NumberFormatException("non-positive price " + price);
                }
                ticks.add(new PriceTick(FeedMessageParser.eventId(SOURCE, trade.get("trade_id")), PriceTick.BTC_USD,
                        price, SOURCE, time, receivedAt));
            } catch (NumberFormatException | DateTimeParseException e) {
                log.debug("Skipping malformed Kraken trade {}: {}", trade, e.getMessage());
            }
        }
        return ticks.isEmpty()
                ? new FeedMessage.Invalid("no valid BTC/USD trade in update")
                : new FeedMessage.Trades(ticks);
    }
}
