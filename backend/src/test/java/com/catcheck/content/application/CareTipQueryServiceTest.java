package com.catcheck.content.application;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.ClaimType;
import com.catcheck.content.domain.port.CareTipRepository;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class CareTipQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    private final CareTipRepository repository = Mockito.mock(CareTipRepository.class);
    private final CareTipQueryService service = new CareTipQueryService(repository);

    @Test
    void slugMissingInTheRequestedLocaleFallsBackToThePublishedVietnameseTip() {
        CareTip vietnamese = published("ve-sinh-khay", "vi");
        CareTip english = published("ve-sinh-khay", "en");
        when(repository.findBySlugAndLocale(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findBySlugAndLocale("ve-sinh-khay", "vi")).thenReturn(Optional.of(vietnamese));

        assertThat(service.getPublishedBySlug("ve-sinh-khay", "en")).isSameAs(vietnamese);

        when(repository.findBySlugAndLocale("ve-sinh-khay", "en")).thenReturn(Optional.of(english));
        assertThat(service.getPublishedBySlug("ve-sinh-khay", "en")).as("requested locale wins").isSameAs(english);
    }

    @Test
    void slugMissingInEveryLocaleIsStillNotFound() {
        when(repository.findBySlugAndLocale(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublishedBySlug("khong-co", "en")).isInstanceOf(NotFoundException.class);
    }

    private static CareTip published(String slug, String locale) {
        CareTip tip = CareTip.createDraft(UUID.randomUUID(), slug, UUID.randomUUID(), locale, CareTipKind.TIP,
                "Vệ sinh khay cát", ClaimType.NONE, UUID.randomUUID(), NOW);
        tip.submitReview(NOW);
        tip.publish(UUID.randomUUID(), "ok", NOW);
        return tip;
    }
}
