package com.catcheck.credit.domain;

/**
 * Mức lịch sử của một gói — giá trị của khoá {@code history} trong
 * {@code package_plan.features} / {@code user_entitlement.features} (p4 §4.4.6).
 *
 * <p>Đây là enum CÓ thuộc tính hiển thị nhưng vẫn nằm trong JSONB theo quyết định đã chốt
 * (p4 §4.1.3: danh sách cờ tính năng thay đổi không cần migration), nên không có CHECK ở DB.
 * Giá trị trên dây khớp đúng ký tự với giá trị ghi trong JSONB.</p>
 */
public enum HistoryLevel {

    /** Không có lịch sử. */
    NONE,

    /** Lịch sử cơ bản. */
    BASIC,

    /** Lịch sử nâng cao. */
    ADVANCED;

    /** Có quyền xem lịch sử hay không. */
    public boolean grantsHistory() {
        return this != NONE;
    }
}
