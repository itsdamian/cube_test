package com.currency.demo.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT /api/currencies. Validated by Bean Validation ({@code @Valid}). */
public record CurrencyRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be 3 uppercase letters (ISO 4217), e.g. TWD")
        String code,

        @NotBlank
        @Size(max = 50)
        String name) {
}
