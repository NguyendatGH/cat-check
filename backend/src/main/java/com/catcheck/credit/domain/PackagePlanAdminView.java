package com.catcheck.credit.domain;

import java.time.Instant;

/**
 * {@link PackagePlan} kèm hai cột chỉ màn admin cần — L25/L26 (p8 §8.4.12).
 *
 * <p>Tách khỏi {@link PackagePlan} thay vì thêm field vào đó: {@code PackagePlan} là
 * <b>bản chụp cấu hình</b> được chép vào {@code credit_batch} lúc kích hoạt, nên mọi field thêm
 * vào nó đều có nguy cơ bị hiểu nhầm là "đã snapshot". {@code updatedAt} chỉ phục vụ
 * {@code ETag}/{@code If-Match} của p8 §8.1.11 và không thuộc về bản chụp đó.</p>
 *
 * @param plan      cấu hình gói
 * @param updatedAt {@code package_plan.updated_at} — nguồn của ETag
 */
public record PackagePlanAdminView(PackagePlan plan, Instant updatedAt) {

    public PackagePlanAdminView {
        if (plan == null) {
            throw new IllegalArgumentException("packagePlanAdminView.plan bắt buộc");
        }
    }

    public String code() {
        return plan.code();
    }
}
