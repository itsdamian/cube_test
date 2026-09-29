package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Coinbase Exchange public feed ({@code wss://ws-feed.exchange.coinbase.com}).
 *
 * <p>Subscribes to {@code ticker} (one message per trade, price as a decimal string) and
 * {@code heartbeat} (one message per second, used by the idle watchdog). Note: on this
 * feed the channel is {@code heartbeat}; {@code heartbeats} belongs to the different
 * "Advanced Trade" API and is rejected here.
 */
public final class CoinbaseMessageParser implements FeedMessageParser {

    public static final String SOURCE = "coinbase";
    private static final String PRODUCT = "BTC-USD";

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public String subscribeMessage() {
        return """
                {"type":"subscribe","product_ids":["BTC-USD"],"channels":["ticker","heartbeat"]}""";
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
        String type = root.path("type").asText("");
        return switch (type) {
            case "ticker" -> ticker(root, receivedAt);
            case "heartbeat" -> new FeedMessage.Heartbeat();
            case "subscriptions" -> new FeedMessage.Control("subscriptions");
            case "error" -> new FeedMessage.ExchangeError(
                    root.path("message").asText() + ": " + root.path("reason").asText());
            default -> new FeedMessage.Invalid("unknown type '" + type + "'");
        };
    }

    private FeedMessage ticker(JsonNode node, Instant receivedAt) {
        if (!PRODUCT.equals(node.path("product_id").asText())) {
            return new FeedMessage.Control("ticker for other product " + node.path("product_id").asText());
        }
        try {
            BigDecimal price = new BigDecimal(node.path("price").asText());
            Instant time = Instant.parse(node.path("time").asText());
            if (price.signum() <= 0) {
                return new FeedMessage.Invalid("non-positive price " + price);
            }
            return new FeedMessage.Trades(List.of(
                    new PriceTick(UUID.randomUUID(), PriceTick.BTC_USD, price, SOURCE, time, receivedAt)));
        } catch (NumberFormatException | DateTimeParseException e) {
            return new FeedMessage.Invalid("bad ticker field: " + e.getMessage());
        }
    }
}
