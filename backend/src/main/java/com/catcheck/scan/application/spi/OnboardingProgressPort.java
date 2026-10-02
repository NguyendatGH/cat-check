package com.catcheck.scan.application.spi;

import java.util.UUID;

/**
 * Cong SPI de {@code scan} danh dau nguoi dung da hoan tat onboarding sau lan quet dau tien.
 *
 * <p>p4 §4.4 dinh nghia {@code app_user.onboarding_status = COMPLETED} la "da quet lan dau".
 * Truoc day KHONG co gi day cot nay toi {@code COMPLETED} — {@code ScanSavedEvent} co phat
 * nhung khong listener nao xu ly (cung lop bug voi {@code CatCreatedEvent}).</p>
 *
 * <p>Vi sao la SPI rieng cua scan chu khong goi thang {@code identity}:
 * {@code scan/package-info.java} chi cho phep phu thuoc
 * {@code shared, credit::api, cat::api, media::api, audit::api, privacy::spi} — khong co
 * {@code identity}. Adapter hien thuc cong nay doc/ghi bang {@code app_user} bang SQL tho,
 * KHONG import type nao cua {@code com.catcheck.identity.*}, dung ky thuat ma
 * {@code scan/package-info.java} da ghi chu cong khai va {@code cat} da dung san.</p>
 */
public interface OnboardingProgressPort {

    /**
     * Danh dau da quet lan dau. Chi tien, khong lui — goi nhieu lan la vo hai (idempotent).
     */
    void markFirstScanCompleted(UUID userId);
}
