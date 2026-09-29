package com.currency.demo.currency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business rules for currencies: codes are unique; missing ids are an error.
 *
 * <p>Uniqueness is checked up front for a friendly message, and the database
 * unique constraint is the final safety net for concurrent requests
 * ({@link DataIntegrityViolationException} is translated to the same 409 error).
 */
@Service
@Transactional(readOnly = true)
public class CurrencyService {

    private final CurrencyRepository repository;

    public CurrencyService(CurrencyRepository repository) {
        this.repository = repository;
    }

    public List<CurrencyResponse> findAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(CurrencyResponse::from).toList();
    }

    public CurrencyResponse findById(long id) {
        return CurrencyResponse.from(load(id));
    }

    @Transactional
    public CurrencyResponse create(CurrencyRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new DuplicateCurrencyCodeException(request.code());
        }
        return save(new Currency(request.code(), request.name()), request.code());
    }

    @Transactional
    public CurrencyResponse update(long id, CurrencyRequest request) {
        Currency currency = load(id);
        repository.findByCode(request.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateCurrencyCodeException(request.code());
                });
        currency.rename(request.code(), request.name());
        return save(currency, request.code());
    }

    @Transactional
    public void delete(long id) {
        repository.delete(load(id));
    }

    private Currency load(long id) {
        return repository.findById(id).orElseThrow(() -> new CurrencyNotFoundException(id));
    }

    private CurrencyResponse save(Currency currency, String code) {
        try {
            return CurrencyResponse.from(repository.saveAndFlush(currency));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCurrencyCodeException(code);
        }
    }
}
