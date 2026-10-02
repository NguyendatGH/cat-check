package com.catcheck.cat.api.dto;

import java.util.List;

/**
 * D20 — body khai dấu hiệu lâm sàng. {@code scanId}/{@code note} được FE gửi tuỳ chọn
 * ({@code features/cat/hooks.ts#useReportClinicalSigns}) nhưng KHÔNG có cột lưu tương ứng ở
 * {@code cat_clinical_sign_report} (V8) — {@code scanId} chờ V11 (module {@code scan}), còn
 * {@code note} tự do chưa có trong p4 C5 nên bị bỏ qua có chủ đích, không lỗi 400.
 */
public record ReportClinicalSignsRequest(
        List<String> signs,
        String source,
        String scanId,
        String note
) {
}
