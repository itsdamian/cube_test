package com.currency.demo.stream;

import com.currency.demo.pricing.FeedStatusTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rolling updates (spec k8s-gitops-cicd, requirement 8): a stopping pod ends its SSE streams at
 * once, so browsers reconnect to another pod instead of waiting ~30 s on a silent connection.
 */
class SseBroadcasterShutdownTest {

    private final SseBroadcaster broadcaster =
            new SseBroadcaster(new FeedStatusTracker(Clock.systemUTC()), new SimpleMeterRegistry(), Clock.systemUTC());

    @AfterEach
    void stop() {
        broadcaster.destroy();
    }

    /** Records whether the stream was ended by the server. */
    static class CompletionRecordingEmitter extends SseEmitter {
        volatile boolean completed;

        @Override
        public void complete() {
            completed = true;
            super.complete();
        }
    }

    @Test
    void shutdownEndsEveryOpenStream() {
        CompletionRecordingEmitter a = new CompletionRecordingEmitter();
        CompletionRecordingEmitter b = new CompletionRecordingEmitter();
        broadcaster.register(a);
        broadcaster.register(b);
        assertThat(broadcaster.connectionCount()).isEqualTo(2);

        broadcaster.closeConnectionsOnShutdown();

        assertThat(a.completed).isTrue();
        assertThat(b.completed).isTrue();
        assertThat(broadcaster.connectionCount()).isZero();
    }

    @Test
    void aStreamOpenedDuringShutdownIsEndedImmediately() {
        broadcaster.closeConnectionsOnShutdown();

        CompletionRecordingEmitter late = new CompletionRecordingEmitter();
        broadcaster.register(late);

        assertThat(late.completed).isTrue();
        assertThat(broadcaster.connectionCount()).isZero();
    }
}
