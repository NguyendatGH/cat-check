package com.catcheck.credit.domain;

/**
 * Cờ tính năng của một gói — nội dung của {@code package_plan.features} và
 * {@code user_entitlement.features} (p5 §5.3, p4 §4.1.5, p4 §4.4.6).
 *
 * <p>JSONB, không có CHECK: danh sách cờ sẽ thêm/bớt theo thời gian, thêm cột cho từng cờ
 * nghĩa là mỗi lần thêm cờ là một migration. Quy tắc của p4 §4.1.5: không lọc, không sắp
 * xếp, không cộng tổng theo từng khoá JSONB này — chỉ đọc cả khối để hiển thị và quyết định
 * mở/khoá tính năng.</p>
 *
 * @param history     mức lịch sử
 * @param trend       có biểu đồ xu hướng
 * @param reminder    có nhắc theo dõi (M5, giữ cờ để entitlement không phải migrate khi mở)
 * @param export      có xuất hồ sơ PDF
 * @param storeImage  có lưu ảnh gốc — quyết định #12: {@code false} thì KHÔNG được ghi ảnh xuống đĩa
 */
public record PlanFeatures(
        HistoryLevel history,
        boolean trend,
        boolean reminder,
        boolean export,
        boolean storeImage
) {

    /**
     * Tập tính năng tối thiểu: user mới chưa kích hoạt gói nào (p5 R6 — 3 lượt trial miễn phí,
     * không lưu ảnh theo quyết định #12, không lịch sử/trend/reminder/export).
     */
    public static PlanFeatures none() {
        return new PlanFeatures(HistoryLevel.NONE, false, false, false, false);
    }

    /**
     * Quyền của gói theo mức này có mở tính năng {@code feature} không.
     *
     * <p>Quyền ĐỌC (lịch sử, trend, export) là quyền vĩnh viễn: p5 R5 nêu rõ nếu mất quyền xem
     * lịch sử ngay khi hết credit, user mất quy cập dữ liệu sức khoẻ của chính con mèo mình.
     * Vì vậy {@code isEnabled} KHÔNG xét {@code writeAccessUntil} — cờ trong
     * {@code user_entitlement.features} đã là kết quả của việc hợp nhất đó rồi.</p>
     */
    public boolean isEnabled(PlanFeature feature) {
        return switch (feature) {
            case HISTORY -> history.grantsHistory();
            case TREND -> trend;
            case REMINDER -> reminder;
            case EXPORT -> export;
            case STORE_IMAGE -> storeImage;
        };
    }
}
