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
        String effectiveDateLabel
) {

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
