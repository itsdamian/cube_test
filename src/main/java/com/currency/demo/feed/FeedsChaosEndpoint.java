package com.currency.demo.feed;

import com.currency.demo.feed.PriceFeedClient.BlockMode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Fault injection for manual acceptance testing (AC6) - registered ONLY with the {@code chaos}
 * profile, and exposed over HTTP only because {@code application-chaos.yml} adds it to
 * {@code management.endpoints.web.exposure.include}. Never enabled by default.
 *
 * <ul>
 *   <li>{@code GET  /actuator/feeds} - active source and each client's state</li>
 *   <li>{@code POST /actuator/feeds/{source}/block} - drop the connection; reconnects fail until unblocked</li>
 *   <li>{@code POST /actuator/feeds/{source}/block?mode=silent} - keep the socket, ignore all messages (half-open)</li>
 *   <li>{@code POST /actuator/feeds/{source}/unblock} - reconnect through the normal path</li>
 * </ul>
 * The real client code (backoff, idle watchdog) and the real failover rules still do the work.
 * Actuator write operations require {@code Content-Type: application/json}, e.g.
 * {@code curl -X POST -H 'Content-Type: application/json' localhost:8080/actuator/feeds/coinbase/block}.
 */
@Component
@Profile("chaos")
@Endpoint(id = "feeds")
public class FeedsChaosEndpoint {

    public record ClientState(String source, boolean connected, BlockMode blockMode) {
    }

    public record FeedsState(String activeSource, FeedStatus status, List<ClientState> clients) {
    }

    private final ObjectProvider<FeedManager> feedManager;

    public FeedsChaosEndpoint(ObjectProvider<FeedManager> feedManager) {
        this.feedManager = feedManager;
    }

    /** 404 when this instance does not run ingest. */
    @ReadOperation
    public WebEndpointResponse<FeedsState> feeds() {
        FeedManager manager = feedManager.getIfAvailable();
        return manager == null ? notFound() : new WebEndpointResponse<>(state(manager));
    }

    /** 404 for an unknown source (or no ingest here); 400 for an unknown action or mode. */
    @WriteOperation
    public WebEndpointResponse<FeedsState> control(@Selector String source, @Selector String action,
                                                   @Nullable String mode) {
        FeedManager manager = feedManager.getIfAvailable();
        PriceFeedClient client = manager == null ? null : manager.clients().stream()
                .filter(c -> c.sourceName().equalsIgnoreCase(source))
                .findFirst().orElse(null);
        if (client == null) {
            return notFound();
        }
        switch (action.toLowerCase(Locale.ROOT)) {
            case "block" -> client.block(parseMode(mode));
            case "unblock" -> client.unblock();
            default -> throw new InvalidEndpointRequestException(
                    "Unknown action '" + action + "' (use block or unblock)", "Unknown action");
        }
        return new WebEndpointResponse<>(state(manager));
    }

    private static <T> WebEndpointResponse<T> notFound() {
        return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NOT_FOUND);
    }

    private static FeedsState state(FeedManager manager) {
        List<ClientState> clients = manager.clients().stream()
                .map(c -> new ClientState(c.sourceName(), c.isConnected(), c.blockMode()))
                .toList();
        return new FeedsState(manager.activeSource(), manager.currentStatus(), clients);
    }

    private static BlockMode parseMode(@Nullable String mode) {
        if (mode == null || mode.isBlank() || mode.equalsIgnoreCase("disconnect")) {
            return BlockMode.DISCONNECT;
        }
        if (mode.equalsIgnoreCase("silent")) {
            return BlockMode.SILENT;
        }
        throw new InvalidEndpointRequestException(
                "Unknown mode '" + mode + "' (use disconnect or silent)", "Unknown mode");
    }
}
