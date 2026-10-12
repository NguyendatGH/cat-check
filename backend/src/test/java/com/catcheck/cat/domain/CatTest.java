package com.catcheck.cat.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CatTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-09");
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    void patchWithBothBirthDateAndApproxAgeMonthsIsRejectedJustLikeCreate() {
        Cat cat = Cat.create(UUID.randomUUID(), UUID.randomUUID(), "Mochi",
                null, 12, "CC-VN-ABCDEF", TODAY, NOW);

        assertThrows(CatAgeConflictException.class, () -> cat.update(
                null, LocalDate.parse("2024-01-01"), true, 6,
                null, null, null, null, null, null, null, TODAY, NOW));
    }

    @Test
    void patchWithOnlyBirthDateStillSwitchesTheAgeSourceAndClearsApproxAgeMonths() {
        Cat cat = Cat.create(UUID.randomUUID(), UUID.randomUUID(), "Mochi",
                null, 12, "CC-VN-ABCDEF", TODAY, NOW);

        cat.update(null, LocalDate.parse("2024-01-01"), true, null,
                null, null, null, null, null, null, null, TODAY, NOW);

        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.parse("2024-01-01"), cat.getBirthDate());
        org.junit.jupiter.api.Assertions.assertEquals(null, cat.getApproxAgeMonths());
    }
}
