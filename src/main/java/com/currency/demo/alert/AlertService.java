package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.alert.AlertDtos.AlertEvent;
import com.currency.demo.alert.AlertDtos.CreateAlert;
import com.currency.demo.pricing.PriceTick;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Transactional
public class AlertService {

    static final int EVENT_LIMIT = 100;

    private final AlertRepository repository;
    private final Clock clock;

    public AlertService(AlertRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Alert create(CreateAlert request) {
        return repository.create(PriceTick.BTC_USD, request.direction(), request.threshold(), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<Alert> list() {
        return repository.findAll();
    }

    public void delete(long id) {
        if (!repository.delete(id)) {
            throw new AlertNotFoundException("Alert", id);
        }
    }

    @Transactional(readOnly = true)
    public List<AlertEvent> events(boolean unreadOnly) {
        return repository.events(unreadOnly, EVENT_LIMIT);
    }

    public void markRead(long eventId) {
        if (!repository.markRead(eventId, clock.instant())) {
            throw new AlertNotFoundException("Alert event", eventId);
        }
    }

    public int markAllRead() {
        return repository.markAllRead(clock.instant());
    }
}
