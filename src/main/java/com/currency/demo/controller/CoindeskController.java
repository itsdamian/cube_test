package com.currency.demo.controller;

import com.currency.demo.service.CoindeskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bitcoin")
public class CoindeskController {

    private final CoindeskService coindeskService;

    @Autowired
    public CoindeskController(CoindeskService coindeskService) {
        this.coindeskService = coindeskService;
    }

    /**
     * Get original Coindesk API data
     */
    @GetMapping("/price/original")
    public ResponseEntity<Map<String, Object>> getOriginalPrice() {
        return ResponseEntity.ok(coindeskService.getOriginalData());
    }

    /**
     * Get transformed Bitcoin price data
     * Format:
     * {
     *   "updateTime": "2023/01/01 00:00:00",
     *   "currencies": {
     *     "USD": {
     *       "code": "USD",
     *       "chineseName": "美元",
     *       "rate": 30123.45
     *     },
     *     "EUR": {
     *       "code": "EUR",
     *       "chineseName": "欧元",
     *       "rate": 27789.12
     *     },
     *     ...
     *   }
     * }
     */
    @GetMapping("/price")
    public ResponseEntity<Map<String, Object>> getTransformedPrice() {
        return ResponseEntity.ok(coindeskService.getTransformedData());
    }
} 