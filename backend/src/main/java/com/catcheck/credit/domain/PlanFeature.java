package com.catcheck.credit.domain;

/**
 * Danh mục tính năng mà entitlement kiểm soát. Dùng cho {@code FEATURE_NOT_IN_PLAN} (p8 §8.2.4(e))
 * và cho module khác hỏi "gói hiện tại có mở tính năng này không" (p8 §8.3.1 bước 4c).
 *
 * <p>Quyền GHI (tạo mới: scan mới, tạo hồ sơ mèo vượt hạn mức, đặt reminder mới) cần có gói
 * còn hiệu lực — đó là {@code user_entitlement.write_access_until}, không phải cờ trong
 * {@code features}. Quyền ĐỌC giữ vĩnh viễn (p5 R5).</p>
 *
 * <p>This enum stays internal. Cross-module callers use
 * {@code EntitlementQuery.Feature}, mapped by the application service.</p>
 */
public enum PlanFeature {

    /** Xem lịch sử quét — {@code features.history} khác {@code NONE}. */
    HISTORY("history"),

    /** Biểu đồ xu hướng. */
    TREND("trend"),

    /** Nhắc theo dõi (M5). */
    REMINDER("reminder"),

    /** Xuất hồ sơ PDF cho bác sĩ thú y. */
    EXPORT("export"),

    /** Lưu ảnh gốc (quyết định #12 + consent {@code SCAN_IMAGE_RETAIN}). */
    STORE_IMAGE("storeImage");

    private final String jsonKey;

    PlanFeature(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    /** Khoá tương ứng trong cột JSONB {@code features}. */
    public String jsonKey() {
        return jsonKey;
    }
}
