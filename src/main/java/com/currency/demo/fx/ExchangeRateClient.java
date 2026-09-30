package com.currency.demo.fx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Reads USD-based rates from open.er-api.com ({@code GET /v6/latest/USD}, no API key).
 * The provider updates once a day; its terms require an attribution link wherever the rates
 * are shown (the frontend adds "Rates By Exchange Rate API").
 */
public class ExchangeRateClient {

    /** The subset of the provider's JSON we use; unknown fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(String result,
                    @JsonProperty("time_last_update_unix") Long timeLastUpdateUnix,
                    @JsonProperty("base_code") String baseCode,
                    Map<String, BigDecimal> rates) {
    }

    private final RestClient http;
    private final URI url;

    public ExchangeRateClient(RestClient http, URI url) {
        this.http = http;
        this.url = url;
    }

    public FxSnapshot fetch() {
        Response body;
        try {
            body = http.get().uri(url).retrieve().body(Response.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new FxUnavailableException("Exchange-rate source rate-limited us (429)", e, true);
        } catch (RestClientException e) {
            // other 4xx/5xx, timeouts, unreadable JSON
            throw new FxUnavailableException("Exchange-rate request failed: " + e.getMessage(), e);
        }
        if (body == null || !"success".equals(body.result()) || !"USD".equals(body.baseCode())
                || body.rates() == null || body.rates().isEmpty() || body.timeLastUpdateUnix() == null) {
            throw new FxUnavailableException("Unexpected exchange-rate payload: " + body, null);
        }
        return new FxSnapshot(Instant.ofEpochSecond(body.timeLastUpdateUnix()), body.rates());
    }
}
