package com.currency.demo.fx;

/** The exchange-rate source could not be read (network error, 429 rate limit, bad payload...). */
public class FxUnavailableException extends RuntimeException {

    public FxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
