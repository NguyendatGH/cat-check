package com.catcheck.content.api.dto;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.ClaimType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CareTipDetailResponseTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void exposesAuthorIdSoTheEditorCanHideSelfPublish() {
        UUID authorId = UUID.randomUUID();
        CareTip tip = CareTip.createDraft(
                UUID.randomUUID(), "uong-nuoc", UUID.randomUUID(), "vi",
                CareTipKind.TIP, "Uống nước", ClaimType.NONE, authorId, NOW);

        CareTipDetailResponse response = CareTipDetailResponse.from(tip);

        assertThat(response.authorId()).isEqualTo(authorId.toString());
        assertThat(response.reviewedBy()).isNull();
    }

    @Test
    void nullAuthorMapsToNull() {
        CareTip tip = CareTip.createDraft(
                UUID.randomUUID(), "uong-nuoc", UUID.randomUUID(), "vi",
                CareTipKind.TIP, "Uống nước", ClaimType.NONE, null, NOW);

        assertThat(CareTipDetailResponse.from(tip).authorId()).isNull();
    }
}
