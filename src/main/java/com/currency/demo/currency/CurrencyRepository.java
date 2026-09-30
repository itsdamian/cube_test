package com.currency.demo.currency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCode(String code);

    boolean existsByCode(String code);

    List<Currency> findAllByOrderByCodeAsc();
}
