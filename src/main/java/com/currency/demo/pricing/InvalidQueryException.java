package com.currency.demo.pricing;

/** A query parameter combination the API rejects with HTTP 400. */
public class InvalidQueryException extends RuntimeException {

    public InvalidQueryException(String message) {
        super(message);
    }
}
