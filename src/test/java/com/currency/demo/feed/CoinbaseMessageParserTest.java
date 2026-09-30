package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CoinbaseMessageParserTest {

    private static final Instant RECEIVED = Instant.parse("2026-09-29T07:37:31.200Z");
    private final CoinbaseMessageParser parser = new CoinbaseMessageParser();

    @Test
    void tickerBecomesOneTickWithExactPriceAndExchangeTime() {
        FeedMessage msg = parser.parse(Fixtures.read("coinbase/ticker.json"), RECEIVED);

        assertThat(msg).isInstanceOf(FeedMessage.Trades.class);
        PriceTick tick = msg.ticks().getFirst();
        assertThat(tick.pair()).isEqualTo("BTC-USD");
        assertThat(tick.price()).isEqualTo(new BigDecimal("84045.5"));
        assertThat(tick.source()).isEqualTo("coinbase");
        assertThat(tick.eventTime()).isEqualTo(Instant.parse("2026-09-29T07:37:31.089264Z"));
        assertThat(tick.receivedAt()).isEqualTo(RECEIVED);
        assertThat(tick.eventId()).isNotNull();
    }

    @Test
    void sameTradeParsedTwiceGetsTheSameEventId() {
        String text = Fixtures.read("coinbase/ticker.json");
        PriceTick first = parser.parse(text, RECEIVED).ticks().getFirst();
        PriceTick again = parser.parse(text, RECEIVED.plusSeconds(5)).ticks().getFirst();
        PriceTick otherTrade = parser.parse(text.replace("1099886673", "1099886674"), RECEIVED).ticks().getFirst();

        assertThat(again.eventId()).isEqualTo(first.eventId());
        assertThat(otherTrade.eventId()).isNotEqualTo(first.eventId());
    }

    @Test
    void heartbeatIsRecognisedButCarriesNoTick() {
        FeedMessage msg = parser.parse(Fixtures.read("coinbase/heartbeat.json"), RECEIVED);

        assertThat(msg).isInstanceOf(FeedMessage.Heartbeat.class);
        assertThat(msg.ticks()).isEmpty();
    }

    @Test
    void subscriptionAckIsControl() {
        assertThat(parser.parse(Fixtures.read("coinbase/subscriptions.json"), RECEIVED))
                .isInstanceOf(FeedMessage.Control.class);
    }

    @Test
    void errorMessageIsReportedAsExchangeError() {
        FeedMessage msg = parser.parse(Fixtures.read("coinbase/error.json"), RECEIVED);

        assertThat(msg).isInstanceOfSatisfying(FeedMessage.ExchangeError.class,
                e -> assertThat(e.message()).contains("heartbeats is not a valid channel"));
    }

    @Test
    void tickerForAnotherProductIsIgnored() {
        String eth = Fixtures.read("coinbase/ticker.json").replace("BTC-USD", "ETH-USD");

        assertThat(parser.parse(eth, RECEIVED).ticks()).isEmpty();
    }

    @Test
    void subscribeMessageAsksForTickerAndHeartbeat() {
        assertThat(parser.subscribeMessage())
                .contains("\"BTC-USD\"").contains("\"ticker\"").contains("\"heartbeat\"")
                .doesNotContain("heartbeats");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[1,2,3]",
            "{\"type\":\"something_new\"}",
            "{\"type\":\"ticker\",\"product_id\":\"BTC-USD\",\"price\":\"abc\",\"time\":\"2026-09-29T07:37:31Z\"}",
            "{\"type\":\"ticker\",\"product_id\":\"BTC-USD\",\"price\":\"1.0\",\"time\":\"yesterday\"}",
            "{\"type\":\"ticker\",\"product_id\":\"BTC-USD\",\"price\":\"-5\",\"time\":\"2026-09-29T07:37:31Z\"}",
            "{\"type\":\"ticker\",\"product_id\":\"BTC-USD\"}"
    })
    void malformedInputGivesEmptyResultAndNeverThrows(String text) {
        assertThatCode(() -> parser.parse(text, RECEIVED)).doesNotThrowAnyException();
        FeedMessage msg = parser.parse(text, RECEIVED);
        assertThat(msg.ticks()).isEmpty();
        assertThat(msg).isInstanceOf(FeedMessage.Invalid.class);
    }
}
