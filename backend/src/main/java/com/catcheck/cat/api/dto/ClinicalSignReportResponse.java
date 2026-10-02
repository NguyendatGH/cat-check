package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatClinicalSignReport;

/**
 * D20 — kết quả khai dấu hiệu lâm sàng. {@code triggeredFlag} luôn {@code null} ở M2: sinh
 * {@code health_flag} là việc của module {@code insight} (chưa tồn tại), nghe qua
 * {@code ClinicalSignsReportedEvent} — xem javadoc {@code ClinicalSignReportService}.
 */
public record ClinicalSignReportResponse(String reportId, TriggeredFlag triggeredFlag) {

    public static ClinicalSignReportResponse from(CatClinicalSignReport report) {
        return new ClinicalSignReportResponse(report.getId().toString(), null);
    }

    /** Hình dạng để dành cho khi insight nối xong — chưa module nào sinh giá trị này ở M2. */
    public record TriggeredFlag(String flagId, String ruleCode, String severity) {
    }
}
