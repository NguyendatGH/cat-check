package com.catcheck.export.application.spi;

import java.util.UUID;

/**
 * Cổng kiểm tra tính năng {@code export} (xuất hồ sơ PDF) có nằm trong gói hiện tại không.
 *
 * <p><b>Bug thật đã sửa:</b> trước bản vá này, {@code ExportRequestService.request()} không kiểm
 * entitlement nào cả — {@code credit.domain.PlanFeature.EXPORT} (JSONB {@code features.export})
 * tồn tại đúng cho việc này (seed {@code R__seed_package_plan.sql}: gói MINI/DAILY
 * {@code "export":false}, PLUS/MULTI/CARE_BOX {@code "export":true}) và {@code reminder}/{@code cat} đã
 * làm y hệt cho tính năng của họ ({@code ReminderFeaturePort}, {@code FeatureEntitlementPort}),
 * nhưng {@code export} thì bỏ sót: mọi user, bất kể gói, đều tạo và tải được PDF xuất hồ sơ cho
 * bác sĩ thú y.</p>
 *
 * <p>Đọc thẳng cột JSONB {@code user_entitlement.features} bằng JDBC, cùng khuôn
 * {@code reminder.application.spi.ReminderFeaturePort}/
 * {@code cat.application.spi.FeatureEntitlementPort} — KHÔNG import type nào của
 * {@code com.catcheck.credit.*}: {@code export::package-info} cố ý không khai
 * {@code credit::api} (xem judgment call ở đó cho {@code scan::api}/{@code cat::api}), nên thêm
 * cạnh phụ thuộc mới cho một lần đọc boolean là không cần thiết.</p>
 *
 * <p>p11 §11.5.5: KHÔNG cache kết quả trong phiên — mỗi request kiểm lại.</p>
 */
public interface ExportFeaturePort {

    boolean isExportEnabled(UUID userId);
}
