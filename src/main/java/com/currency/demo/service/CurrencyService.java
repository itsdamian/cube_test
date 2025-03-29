package com.currency.demo.service;

import com.currency.demo.model.Currency;
import com.currency.demo.repository.CurrencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    @Autowired
    public CurrencyService(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    public List<Currency> getAllCurrencies() {
        return currencyRepository.findAll();
    }

    public Optional<Currency> getCurrencyById(Long id) {
        return currencyRepository.findById(id);
    }

    public Currency getCurrencyByCode(String code) {
        return currencyRepository.findByCode(code);
    }

    @Transactional
    public Currency createCurrency(Currency currency) {
        return currencyRepository.save(currency);
    }

    @Transactional
    public Currency updateCurrency(Long id, Currency currency) {
        Optional<Currency> existingCurrency = currencyRepository.findById(id);
        if (existingCurrency.isPresent()) {
            Currency updatedCurrency = existingCurrency.get();
            updatedCurrency.setCode(currency.getCode());
            updatedCurrency.setName(currency.getName());
            return currencyRepository.save(updatedCurrency);
        }
        return null;
    }

    @Transactional
    public void deleteCurrency(Long id) {
        currencyRepository.deleteById(id);
    }
} 