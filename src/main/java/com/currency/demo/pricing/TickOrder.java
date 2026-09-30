package com.currency.demo.pricing;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.UUID;

/**
 * The single definition of "which tick came first": (event_time, received_at, event_id).
 *
 * <p>Used for candle open/close in Kafka Streams and must match PostgreSQL's
 * {@code ORDER BY event_time, received_at, event_id}, so that candles always agree with the
 * stored ticks (AC4). Two details make that true:
 * <ul>
 *   <li>times are compared at microsecond precision, which is what PostgreSQL stores;</li>
 *   <li>UUIDs are compared as unsigned bytes like PostgreSQL does. Java's own
 *       {@link UUID#compareTo} compares <em>signed</em> longs and disagrees whenever the top
 *       bit is set.</li>
 * </ul>
 */
public final class TickOrder {

    public static final Comparator<PriceTick> COMPARATOR = Comparator
            .comparing((PriceTick t) -> t.eventTime().truncatedTo(ChronoUnit.MICROS))
            .thenComparing(t -> t.receivedAt().truncatedTo(ChronoUnit.MICROS))
            .thenComparing(PriceTick::eventId, TickOrder::compareUnsigned);

    private TickOrder() {
    }

    static int compareUnsigned(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
