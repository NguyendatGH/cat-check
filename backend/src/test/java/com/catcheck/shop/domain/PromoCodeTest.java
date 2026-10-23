package com.catcheck.shop.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromoCodeTest {

    private PromoCode sample(String code, String type, long amount, long minOrder, Long maxDiscount, Integer limit, int used, Instant startsAt, Instant expiresAt, boolean active) {
        return new PromoCode(UUID.randomUUID(), code, "Sample", type, amount, minOrder, maxDiscount, limit, used, startsAt, expiresAt, active, Instant.now(), Instant.now());
    }

    @Test
    void percentDiscountCalculatedWithOptionalCap() {
        PromoCode p1 = sample("P10", "PERCENT", 10, 0, null, null, 0, null, null, true);
        assertThat(p1.calculateDiscount(200_000, 30_000)).isEqualTo(20_000);

        // Cap at 15,000
        PromoCode pCapped = sample("PCAP", "PERCENT", 10, 0, 15_000L, null, 0, null, null, true);
        assertThat(pCapped.calculateDiscount(200_000, 30_000)).isEqualTo(15_000);
    }

    @Test
    void shippingDiscountCappedAtActualShippingFee() {
        PromoCode freeship = sample("FREESHIP", "SHIPPING", 30_000, 0, null, null, 0, null, null, true);
        assertThat(freeship.calculateDiscount(200_000, 30_000)).isEqualTo(30_000);
        assertThat(freeship.calculateDiscount(200_000, 15_000)).isEqualTo(15_000);
        assertThat(freeship.calculateDiscount(500_000, 0)).isZero();
    }

    @Test
    void fixedDiscountCappedAtSubtotalPlusShipping() {
        PromoCode fixed = sample("FIXED", "FIXED", 20_000, 0, null, null, 0, null, null, true);
        assertThat(fixed.calculateDiscount(100_000, 30_000)).isEqualTo(20_000);
        assertThat(fixed.calculateDiscount(10_000, 5_000)).isEqualTo(15_000);
    }

    @Test
    void validatesActiveAndLimitsAndMinOrder() {
        Instant now = Instant.parse("2026-10-23T12:00:00Z");

        // Inactive
        PromoCode inactive = sample("INACT", "FIXED", 10_000, 0, null, null, 0, null, null, false);
        assertThat(inactive.isValid(now, 100_000)).isFalse();

        // Expired
        PromoCode expired = sample("EXP", "FIXED", 10_000, 0, null, null, 0, null, now.minusSeconds(10), true);
        assertThat(expired.isValid(now, 100_000)).isFalse();

        // Not started yet
        PromoCode future = sample("FUT", "FIXED", 10_000, 0, null, null, 0, now.plusSeconds(10), null, true);
        assertThat(future.isValid(now, 100_000)).isFalse();

        // Usage limit reached
        PromoCode limitReached = sample("LIM", "FIXED", 10_000, 0, null, 5, 5, null, null, true);
        assertThat(limitReached.isValid(now, 100_000)).isFalse();

        // Min order not reached
        PromoCode minOrder = sample("MIN", "FIXED", 10_000, 300_000, null, null, 0, null, null, true);
        assertThat(minOrder.isValid(now, 200_000)).isFalse();
        assertThat(minOrder.isValid(now, 350_000)).isTrue();
    }
}
