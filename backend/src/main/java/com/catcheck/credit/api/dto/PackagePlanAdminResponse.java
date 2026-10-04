package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.PackagePlanAdminView;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một gói ở màn quản trị — L25/L26 (p8 §8.4.12, cột ở p14 §14.3.2 mục 5).
 *
 * <p>Khác {@link PackagePlanResponse} (F3, công khai) ở ba điểm, đều có lý do:</p>
 * <ul>
 *   <li>có {@code active} và {@code version} — màn admin phải thấy gói đã ngừng bán và phải
 *       thấy version để hiểu {@code credit_batch.package_version};</li>
 *   <li>cờ tính năng trả <b>lồng</b> trong {@code features} thay vì phẳng: form admin sửa cả
 *       khối JSONB bằng một JsonEditor (p14 §14.3.2 mục 5), còn F3 trả phẳng để khớp
 *       {@code EntitlementResponse} mà client đã dùng;</li>
 *   <li>có {@code etag} — L26 bắt buộc {@code If-Match} (p8 §8.1.11), trả sẵn trong body để UI
 *       giữ kèm từng dòng mà không phải gọi lại từng gói chỉ để đọc header.</li>
 * </ul>
 *
 * @param maxCatProfiles {@code null} = không giới hạn (p4 I27)
 */
public record PackagePlanAdminResponse(
        String code,
        String name,
        BigDecimal weightKg,
        int creditAmount,
        int creditValidityDays,
        Integer maxCatProfiles,
        PlanFeaturesView features,
        boolean active,
        int version,
        Instant updatedAt,
        String etag
) {

    public static PackagePlanAdminResponse from(PackagePlanAdminView view, String etag) {
        var plan = view.plan();
        return new PackagePlanAdminResponse(
                plan.code(),
                plan.name(),
                plan.weightKg(),
                plan.creditAmount(),
                plan.creditValidityDays(),
                plan.maxCatProfiles(),
                PlanFeaturesView.from(plan),
                plan.active(),
                plan.version(),
                view.updatedAt(),
                etag);
    }
}
