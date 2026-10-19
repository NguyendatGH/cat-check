package com.catcheck.scan.api.dto;

import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.port.ScanQueryRepository.Row;

import java.math.BigDecimal;
import java.time.Instant;

public record ScanListItemResponse(
        String scanId,
        String catId,
        String catName,
        Instant capturedAt,
        BigDecimal phValue,
        String classification,
        String bandCode,
        BigDecimal confidence,
        String confidenceBand,
        boolean nearBoundary,
        String thumbnailHex,
        boolean imageAvailable,
        boolean disputed,
        boolean hasNote
) {

    public static ScanListItemResponse from(Row row, String catName) {
        String hex = row.labL() == null ? null
                : ColorSpace.labToHex(new Lab(
                        row.labL().doubleValue(), row.labA().doubleValue(), row.labB().doubleValue()));
        return new ScanListItemResponse(
                row.scanId().toString(),
                row.catId() == null ? null : row.catId().toString(),
                catName,
                row.capturedAt(),
                row.phValue(),
                row.classification(),
                row.classification(),
                row.confidence(),
                confidenceBandOf(row.confidence()),
                row.nearBoundary(),
                hex,
                row.imageStored(),
                row.disputedAt() != null,
                false);
    }

    private static String confidenceBandOf(BigDecimal confidence) {
        if (confidence == null) {
            return null;
        }
        double v = confidence.doubleValue();
        if (v >= 0.75) {
            return "HIGH";
        }
        return v >= 0.40 ? "MEDIUM" : "LOW";
    }
}
