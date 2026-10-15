package com.catcheck.export.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

record ExportRenderData(
        String documentCode,
        String catName,
        String breedLabel,
        String sexLabel,
        String ageLabel,
        String weightLabel,
        String ownerName,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        LocalDateTime generatedAtLocal,
        List<ScanRow> scans,
        long validCount,
        BigDecimal minPh,
        BigDecimal maxPh,
        BigDecimal medianPh,
        long outOfRangeCount,
        long nearBoundaryCount,
        String svgChart,
        boolean anyNonCardCcm,
        List<FlagRow> flags,
        Integer chartVersionSnapshot,
        String effectiveDateLabel,
        List<String> sections
) {

    /**
     * Khối tuỳ chọn user đã chọn ({@code export_job.sections}: TREND/SCAN_LOG/NOTES/PROFILE).
     * {@code null}/rỗng (dòng cũ) coi như chọn tất cả. Các khối bắt buộc của p13 §13.3 (bìa,
     * disclaimer, tóm tắt, phương pháp, chân trang) luôn render, không phụ thuộc giá trị này.
     */
    boolean includes(String section) {
        return sections == null || sections.isEmpty() || sections.contains(section);
    }

    record ScanRow(
            String capturedAtLabel,
            BigDecimal phValue,
            String classificationSymbol,
            String classificationLabel,
            String confidenceLabel,
            String qualityFlagLabel,
            boolean disputed,
            boolean imageAvailable
    ) {
    }

    record FlagRow(String ruleCode, String severity, String explanationVi, Instant triggeredAt) {
    }
}
