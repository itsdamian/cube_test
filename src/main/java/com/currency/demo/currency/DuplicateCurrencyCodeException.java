package com.currency.demo.currency;

public class DuplicateCurrencyCodeException extends RuntimeException {

    public DuplicateCurrencyCodeException(String code) {
        super("Currency code " + code + " already exists");
    }
}
