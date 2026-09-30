package com.currency.demo.alert;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(String what, long id) {
        super(what + " " + id + " not found");
    }
}
