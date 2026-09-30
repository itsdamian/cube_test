package com.currency.demo.alert;

import com.currency.demo.alert.AlertDtos.Alert;
import com.currency.demo.alert.AlertDtos.AlertEvent;
import com.currency.demo.alert.AlertDtos.CreateAlert;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/** Price alerts (create / list / delete - no update, per spec) and their firing history. */
@RestController
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping("/api/alerts")
    public List<Alert> list() {
        return service.list();
    }

    @PostMapping("/api/alerts")
    public ResponseEntity<Alert> create(@Valid @RequestBody CreateAlert request) {
        Alert created = service.create(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @DeleteMapping("/api/alerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(id);
    }

    /** Most recent 100 events; {@code unread=true} returns only those not yet seen. */
    @GetMapping("/api/alert-events")
    public List<AlertEvent> events(@RequestParam(defaultValue = "false") boolean unread) {
        return service.events(unread);
    }

    @PostMapping("/api/alert-events/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable long id) {
        service.markRead(id);
    }

    @PostMapping("/api/alert-events/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        service.markAllRead();
    }
}
