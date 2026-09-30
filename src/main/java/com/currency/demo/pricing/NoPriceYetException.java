package com.currency.demo.pricing;

/** No tick has been stored yet (HTTP 404). */
public class NoPriceYetException extends RuntimeException {

    public NoPriceYetException() {
        super("No price has been received yet");
    }
}
