package com.catcheck.content.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CareTipTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Test
    void publishRequiresTheReviewerWhoWillBeRecordedOnTheCareTip() {
        CareTip tip = draft();
        tip.submitReview(NOW);

        assertThatNullPointerException()
                .isThrownBy(() -> tip.publish(null, "Reviewed carefully", NOW))
                .withMessageContaining("reviewerId");

        assertThat(tip.getStatus()).isEqualTo(CareTipStatus.IN_REVIEW);
        assertThat(tip.getReviewedBy()).isNull();
    }

    private static CareTip draft() {
        return CareTip.createDraft(
                UUID.randomUUID(), "hydration", UUID.randomUUID(), "en", CareTipKind.TIP,
                "Keep fresh water available", ClaimType.NONE, UUID.randomUUID(), NOW);
    }
}
