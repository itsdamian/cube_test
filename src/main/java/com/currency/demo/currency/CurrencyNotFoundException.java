package com.currency.demo.currency;

public class CurrencyNotFoundException extends RuntimeException {

    public CurrencyNotFoundException(long id) {
        super("Currency " + id + " not found");
    }
}
