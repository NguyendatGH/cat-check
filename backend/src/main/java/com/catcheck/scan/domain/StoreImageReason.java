package com.catcheck.scan.domain;

/**
 * Lý do KHÔNG lưu ảnh gốc — cột {@code scan.store_image_reason} (p4 D1).
 *
 * <p>{@code CHECK (store_image OR store_image_reason IS NOT NULL)}: bắt buộc có giá trị khi
 * {@code store_image = false}. p8 {@code SCAN_IMAGE_NOT_STORED} trả nguyên giá trị này trong
 * {@code params.reason}.
 */
public enum StoreImageReason {
    /** Scan trial (chưa từng kích hoạt gói nào) — {@code is_trial = true} luôn kéo theo cờ này. */
    TRIAL,
    /** Gói hiện tại không bật tính năng lưu ảnh ({@code PlanFeature.STORE_IMAGE = false}). */
    PLAN_OFF,
    /** User đã rút consent {@code SCAN_IMAGE_RETAIN}. */
    CONSENT_OFF,
    /** Kết quả {@code INCONCLUSIVE} — ảnh không đạt, không lưu dù gói có bật tính năng. */
    INCONCLUSIVE
}
