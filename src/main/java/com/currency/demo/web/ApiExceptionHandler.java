package com.currency.demo.web;

import com.currency.demo.currency.CurrencyNotFoundException;
import com.currency.demo.currency.DuplicateCurrencyCodeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} JSON responses.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} makes Spring's own errors
 * (e.g. Bean Validation failures -> 400, malformed JSON) use the same format;
 * the handlers below map our domain exceptions to 404 / 409.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CurrencyNotFoundException.class)
    ProblemDetail notFound(CurrencyNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateCurrencyCodeException.class)
    ProblemDetail conflict(DuplicateCurrencyCodeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
