package com.catcheck.credit.domain;

import java.math.BigDecimal;

/**
 * Snapshot cấu hình một gói, đọc từ {@code package_plan} (bảng do module cat/content tạo ở V7).
 *
 * <p>Đây là <b>bản chụp</b>, không phải entity sống: khi kích hoạt, {@code credit_batch} lưu
 * {@code package_code} + {@code package_version} cùng {@code initial_amount} đã snapshot, nên
 * admin đổi {@code package_plan.credit_amount} sau đó KHÔNG hồi tố các batch đã phát hành
 * (p5 R1, p17 C12).</p>
 *
 * @param code                 mã gói, khoá tự nhiên của {@code package_plan} (p4 §4.1.1)
 * @param name                 tên hiển thị
 * @param weightKg             khối lượng bao — {@code NUMERIC(4,2) NOT NULL} trong V7, mọi gói
 *                             đều quy về kg
 * @param creditAmount         số credit mỗi lần kích hoạt
 * @param creditValidityDays   hạn credit, tính từ {@code activated_at}
 * @param maxCatProfiles       số hồ sơ mèo tối đa, {@code null} = không giới hạn (p5 §5.3)
 * @param features             cờ tính năng
 * @param active               gói còn được bán không
 * @param version              version cấu hình, chụp vào {@code credit_batch.package_version}
 */
public record PackagePlan(
        String code,
        String name,
        BigDecimal weightKg,
        int creditAmount,
        int creditValidityDays,
        Integer maxCatProfiles,
        PlanFeatures features,
        boolean active,
        int version
) {

    public PackagePlan {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("packagePlan.code phải có giá trị");
        }
        if (creditAmount <= 0) {
            throw new IllegalArgumentException("packagePlan.creditAmount phải > 0: " + code);
        }
        if (creditValidityDays <= 0) {
            throw new IllegalArgumentException("packagePlan.creditValidityDays phải > 0: " + code);
        }
        if (features == null) {
            features = PlanFeatures.none();
        }
    }

    /** Mức ưu tiên của gói này (p5 R5 — entitlement lấy gói cao nhất từng kích hoạt). */
    public int tierRank() {
        return PlanTier.rankOf(code);
    }
}
