package com.currency.demo.stream;

import com.currency.demo.pricing.FeedStatusTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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

    /** Stands in for the Kafka listener containers: records what it sees when it is stopped. */
    static class StopObserver implements SmartLifecycle {
        CompletionRecordingEmitter watched;
        volatile Boolean streamEndedWhenStopped;
        private volatile boolean running;

        @Override public void start() { running = true; }
        @Override public void stop() { streamEndedWhenStopped = watched.completed; running = false; }
        @Override public boolean isRunning() { return running; }
    }

    @Test
    void closingTheSpringContextEndsTheStreamsBeforeAnythingIsStopped() {
        // QA K7: test the @EventListener wiring, not just the method. Spring stops the Kafka
        // listeners (a SmartLifecycle) and only destroys beans at the very end; the streams must
        // already be ended when the first component stops, not merely at bean destruction.
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FeedStatusTracker.class, Clock.systemUTC());
            context.registerBean(SimpleMeterRegistry.class);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(SseBroadcaster.class);
            context.registerBean(StopObserver.class);
            context.refresh();
            CompletionRecordingEmitter open = new CompletionRecordingEmitter();
            context.getBean(SseBroadcaster.class).register(open);
            StopObserver observer = context.getBean(StopObserver.class);
            observer.watched = open;

            context.close();

            assertThat(observer.streamEndedWhenStopped).as("stream ended before lifecycle stop").isTrue();
        }
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
