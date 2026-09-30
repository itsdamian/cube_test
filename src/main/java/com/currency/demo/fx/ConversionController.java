package com.currency.demo.fx;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConversionController {

    private final ConversionService service;

    public ConversionController(ConversionService service) {
        this.service = service;
    }

    /** 404 ProblemDetail until the first price has been stored. */
    @GetMapping("/api/prices/converted")
    public ConvertedPrices converted() {
        return service.converted();
    }
}
