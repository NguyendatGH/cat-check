package com.catcheck.cat.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D4 — merge-patch của {@link Cat#update}. p8 §8.5.3 định nghĩa {@code CAT_AGE_CONFLICT}: một
 * hồ sơ chỉ được có {@code birthDate} HOẶC {@code approxAgeMonths}, không cả hai. {@link Cat#create}
 * đã ép bất biến này từ đầu ({@link Cat#applyAgeSource} via lớp bao
 * {@code CatProfileServiceTest}-style) — bài test này khoá lại rằng PATCH (D4) không có đường nào
 * lách qua cùng bất biến đó.
 */
class CatTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-09");
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");

    @Test
    void patchWithBothBirthDateAndApproxAgeMonthsIsRejectedJustLikeCreate() {
        Cat cat = Cat.create(UUID.randomUUID(), UUID.randomUUID(), "Mochi",
                null, 12, "CC-VN-ABCDEF", TODAY, NOW);

        // Bug that da sua: truoc day nhanh "ca hai deu khac null" khong ton tai trong
        // applyAgePatch(), nen PATCH gui CA HAI truong tuoi se am tham chon birthDate, gan
        // approxAgeMonths = null va bo qua gia tri client vua gui — khong nem loi nao ca, trong
        // khi Cat.create() voi cung dau vao se nem CAT_AGE_CONFLICT. Patch phai nhat quan voi
        // create: tu choi, khong tu y chon giup client.
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
