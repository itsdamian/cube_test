package com.currency.demo.fx;

/** The exchange-rate source could not be read (network error, 429 rate limit, bad payload...). */
public class FxUnavailableException extends RuntimeException {

    private final boolean rateLimited;

    public FxUnavailableException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public FxUnavailableException(String message, Throwable cause, boolean rateLimited) {
        super(message, cause);
        this.rateLimited = rateLimited;
    }

    /** True for HTTP 429: the provider asks us to back off (its limit resets after 20 minutes). */
    public boolean isRateLimited() {
        return rateLimited;
    }
}
