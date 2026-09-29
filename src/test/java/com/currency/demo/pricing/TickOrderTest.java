package com.currency.demo.pricing;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TickOrderTest {

    @Test
    void uuidsCompareAsUnsignedBytesLikePostgres() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000000");
        UUID mid = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");

        assertThat(TickOrder.compareUnsigned(low, mid)).isNegative();
        assertThat(TickOrder.compareUnsigned(mid, high)).isNegative();
        // Java's signed comparison gets this one "wrong" for our purpose:
        assertThat(mid.compareTo(high)).isPositive();
    }
}
