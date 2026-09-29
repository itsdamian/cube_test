package com.currency.demo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * All application-specific settings ({@code app.*}) in one typed, immutable place.
 *
 * <p>Every value has a default in {@code application.yml} and can be overridden by an
 * environment variable (e.g. {@code APP_FEED_STALE_THRESHOLD=20s}), which is how
 * docker compose and, later, Kubernetes will configure the app. Spring converts
 * strings such as {@code 10s}, {@code 30m} or {@code P30D} into {@link Duration}.
 * {@code @Validated} makes startup fail fast if a required value is missing.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Toggle ingest,
        @Valid @NotNull Toggle streams,
        @Valid @NotNull Toggle persist,
        @Valid @NotNull Toggle alerts,
        @Valid @NotNull Feed feed,
        @Valid @NotNull Fx fx,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Alert alert) {

    /** A feature switch, e.g. {@code app.ingest.enabled}. */
    public record Toggle(boolean enabled) {
    }

    /** Realtime price sources (primary + hot-standby backup) and failover timing. */
    public record Feed(
            @Valid @NotNull Source primary,
            @Valid @NotNull Source backup,
            @NotNull Duration staleThreshold,
            @NotNull Duration recoveryPeriod,
            @NotNull Duration idleTimeout,
            @NotNull Duration reconnectInitialBackoff,
            @NotNull Duration reconnectMaxBackoff) {
    }

    /** One exchange WebSocket endpoint. */
    public record Source(@NotNull String name, @NotNull URI url) {
    }

    /** Exchange-rate (fiat) source. */
    public record Fx(@NotNull URI url, @NotNull Duration refreshInterval, boolean refreshEnabled) {
    }

    /** How long raw ticks are kept, and how often the cleanup job runs. */
    public record Retention(@NotNull Duration ticks, @NotNull Duration interval) {
    }

    /** Price-alert behaviour. */
    public record Alert(@NotNull Duration cooldown) {
    }
}
