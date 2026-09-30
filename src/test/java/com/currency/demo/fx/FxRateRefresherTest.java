package com.currency.demo.fx;

import com.currency.demo.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** QA CONCERN 18: after a failed download, retry soon instead of waiting for the next 30-minute run. */
class FxRateRefresherTest {

    private static final Instant NOW = Instant.parse("2026-09-30T09:00:00Z");
    private final ExchangeRateClient client = mock(ExchangeRateClient.class);
    private final FxRateRepository repository = mock(FxRateRepository.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final FxRateRefresher refresher = new FxRateRefresher(client, repository, new MutableClock(NOW), scheduler);

    {
        doReturn(future).when(scheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void networkFailureSchedulesOneRetryAfterOneMinute() {
        when(client.fetch()).thenThrow(new FxUnavailableException("timeout", null));

        assertThat(refresher.refresh()).isFalse();
        refresher.refresh();                                  // still failing, retry already pending

        verify(scheduler, times(1)).schedule(any(Runnable.class), eq(NOW.plusSeconds(60)));
    }

    @Test
    void rateLimitSchedulesTheRetryAfterTwentyMinutes() {
        when(client.fetch()).thenThrow(new FxUnavailableException("429", null, true));

        refresher.refresh();

        verify(scheduler).schedule(any(Runnable.class), eq(NOW.plusSeconds(20 * 60)));
    }

    @Test
    void successSchedulesNothingAndCancelsAPendingRetry() {
        when(client.fetch())
                .thenThrow(new FxUnavailableException("timeout", null))
                .thenReturn(new FxSnapshot(NOW, Map.of("USD", BigDecimal.ONE)));

        refresher.refresh();
        assertThat(refresher.refresh()).isTrue();

        verify(future).cancel(false);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void successOnFirstTryNeedsNoRetry() {
        when(client.fetch()).thenReturn(new FxSnapshot(NOW, Map.of("USD", BigDecimal.ONE)));

        refresher.refresh();

        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }
}
