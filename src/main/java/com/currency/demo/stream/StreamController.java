package com.currency.demo.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events for the browser: {@code new EventSource("/api/stream")}.
 * Events: {@code price}, {@code status}, {@code alert}. The browser reconnects automatically.
 */
@RestController
public class StreamController {

    private final SseBroadcaster broadcaster;

    public StreamController(SseBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.connect();
    }
}
