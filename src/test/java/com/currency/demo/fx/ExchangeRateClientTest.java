package com.currency.demo.fx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The HTTP client against MockRestServiceServer - never the real open.er-api.com (requirement 18). */
class ExchangeRateClientTest {

    private static final URI URL = URI.create("http://127.0.0.1:1/v6/latest/USD");
    private MockRestServiceServer server;
    private ExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExchangeRateClient(builder.build(), URL);
    }

    @Test
    void parsesTheCapturedRealResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                new ClassPathResource("fixtures/fx/open-er-api-latest-usd.json"), MediaType.APPLICATION_JSON));

        FxSnapshot snapshot = client.fetch();

        assertThat(snapshot.providerUpdatedAt()).isEqualTo(Instant.parse("2026-09-30T00:02:31Z"));
        assertThat(snapshot.ratesPerUsd()).hasSize(166);
        assertThat(snapshot.ratesPerUsd().get("TWD")).isEqualByComparingTo(new BigDecimal("31.84071"));
        assertThat(snapshot.ratesPerUsd().get("JPY")).isEqualByComparingTo(new BigDecimal("157.389062"));
        assertThat(snapshot.ratesPerUsd().get("USD")).isEqualByComparingTo(BigDecimal.ONE);
        server.verify();
    }

    @Test
    void rateLimitIsReportedAsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(client::fetch).isInstanceOf(FxUnavailableException.class).hasMessageContaining("429")
                .satisfies(e -> assertThat(((FxUnavailableException) e).isRateLimited()).isTrue());
    }

    @Test
    void serverErrorIsReportedAsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(client::fetch).isInstanceOf(FxUnavailableException.class)
                .satisfies(e -> assertThat(((FxUnavailableException) e).isRateLimited()).isFalse());
    }

    @Test
    void errorPayloadIsReportedAsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"result\":\"error\",\"error-type\":\"unsupported-code\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::fetch).isInstanceOf(FxUnavailableException.class);
    }
}
