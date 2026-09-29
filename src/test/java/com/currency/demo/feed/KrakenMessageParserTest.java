package com.currency.demo.feed;

import com.currency.demo.pricing.PriceTick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KrakenMessageParserTest {

    private static final Instant RECEIVED = Instant.parse("2026-09-29T07:37:36Z");
    private final KrakenMessageParser parser = new KrakenMessageParser();

    @Test
    void oneMessageWithThreeTradesBecomesThreeTicks() {
        FeedMessage msg = parser.parse(Fixtures.read("kraken/trade-multi.json"), RECEIVED);

        List<PriceTick> ticks = msg.ticks();
        assertThat(ticks).hasSize(3);
        assertThat(ticks).extracting(PriceTick::price).containsExactly(
                new BigDecimal("84034.9"), new BigDecimal("84035.0"), new BigDecimal("84036.1"));
        assertThat(ticks).extracting(PriceTick::eventTime).containsExactly(
                Instant.parse("2026-09-29T07:37:33.786450Z"),
                Instant.parse("2026-09-29T07:37:33.888245Z"),
                Instant.parse("2026-09-29T07:37:35.516800Z"));
        assertThat(ticks).allSatisfy(t -> {
            assertThat(t.pair()).isEqualTo("BTC-USD");
            assertThat(t.source()).isEqualTo("kraken");
            assertThat(t.receivedAt()).isEqualTo(RECEIVED);
        });
        assertThat(ticks).extracting(PriceTick::eventId).doesNotHaveDuplicates();
    }

    @Test
    void jsonNumberPriceKeepsExactDecimalDigits() {
        // 84035.0 must stay "84035.0" (scale 1), not become a double like 84035.0000000001.
        BigDecimal price = parser.parse(Fixtures.read("kraken/trade-multi.json"), RECEIVED).ticks().get(1).price();
        assertThat(price.toPlainString()).isEqualTo("84035.0");
    }

    @Test
    void heartbeatIsRecognisedButCarriesNoTick() {
        FeedMessage msg = parser.parse(Fixtures.read("kraken/heartbeat.json"), RECEIVED);

        assertThat(msg).isInstanceOf(FeedMessage.Heartbeat.class);
        assertThat(msg.ticks()).isEmpty();
    }

    @Test
    void statusAndSubscribeAckAreControl() {
        assertThat(parser.parse(Fixtures.read("kraken/status.json"), RECEIVED)).isInstanceOf(FeedMessage.Control.class);
        assertThat(parser.parse(Fixtures.read("kraken/subscribe-ack.json"), RECEIVED)).isInstanceOf(FeedMessage.Control.class);
    }

    @Test
    void rejectedSubscriptionIsExchangeError() {
        FeedMessage msg = parser.parse(Fixtures.read("kraken/subscribe-error.json"), RECEIVED);

        assertThat(msg).isInstanceOfSatisfying(FeedMessage.ExchangeError.class,
                e -> assertThat(e.message()).contains("Currency pair not supported"));
    }

    @Test
    void snapshotOfOldTradesIsIgnored() {
        FeedMessage msg = parser.parse(Fixtures.read("kraken/trade-snapshot.json"), RECEIVED);

        assertThat(msg).isInstanceOf(FeedMessage.Control.class);
        assertThat(msg.ticks()).isEmpty();
    }

    @Test
    void malformedTradeInsideABatchIsSkippedButTheOthersAreKept() {
        String text = """
                {"channel":"trade","type":"update","data":[
                  {"symbol":"BTC/USD","price":"oops","timestamp":"2026-09-29T07:37:33Z"},
                  {"symbol":"BTC/USD","price":84000.1,"timestamp":"2026-09-29T07:37:34Z"},
                  {"symbol":"ETH/USD","price":3000.0,"timestamp":"2026-09-29T07:37:34Z"}]}""";

        assertThat(parser.parse(text, RECEIVED).ticks())
                .extracting(PriceTick::price).containsExactly(new BigDecimal("84000.1"));
    }

    @Test
    void subscribeMessageDisablesSnapshot() {
        assertThat(parser.subscribeMessage())
                .contains("\"channel\":\"trade\"").contains("\"BTC/USD\"").contains("\"snapshot\":false");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{{{",
            "42",
            "{\"channel\":\"book\"}",
            "{\"channel\":\"trade\",\"type\":\"update\",\"data\":[]}",
            "{\"channel\":\"trade\",\"type\":\"update\"}",
            "{\"channel\":\"trade\",\"type\":\"update\",\"data\":[{\"symbol\":\"BTC/USD\",\"price\":1.0,\"timestamp\":\"nope\"}]}",
            "{\"channel\":\"trade\",\"type\":\"update\",\"data\":[{\"symbol\":\"BTC/USD\",\"price\":0,\"timestamp\":\"2026-09-29T07:37:34Z\"}]}"
    })
    void malformedInputGivesEmptyResultAndNeverThrows(String text) {
        assertThatCode(() -> parser.parse(text, RECEIVED)).doesNotThrowAnyException();
        FeedMessage msg = parser.parse(text, RECEIVED);
        assertThat(msg.ticks()).isEmpty();
        assertThat(msg).isInstanceOf(FeedMessage.Invalid.class);
    }
}
