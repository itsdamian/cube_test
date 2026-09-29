package com.currency.demo.web;

import com.currency.demo.currency.CurrencyNotFoundException;
import com.currency.demo.currency.DuplicateCurrencyCodeException;
import com.currency.demo.pricing.InvalidQueryException;
import com.currency.demo.pricing.NoPriceYetException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} JSON responses.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} makes Spring's own errors
 * (e.g. Bean Validation failures -> 400, malformed JSON) use the same format;
 * the handlers below map our domain exceptions to 404 / 409.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** One invalid request field, e.g. {@code {"field":"code","message":"must be 3 uppercase letters..."}}. */
    public record FieldError(String field, String message) {
    }

    /**
     * Bean Validation failure on a {@code @Valid @RequestBody}: keep Spring's 400 ProblemDetail
     * but add an {@code errors} array so the frontend can show which field is wrong and why.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(InvalidQueryException.class)
    ProblemDetail badQuery(InvalidQueryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoPriceYetException.class)
    ProblemDetail noPrice(NoPriceYetException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CurrencyNotFoundException.class)
    ProblemDetail notFound(CurrencyNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DuplicateCurrencyCodeException.class)
    ProblemDetail conflict(DuplicateCurrencyCodeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
