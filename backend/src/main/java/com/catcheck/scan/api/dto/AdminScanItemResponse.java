package com.catcheck.scan.api.dto;

import com.catcheck.scan.application.AdminFreeTextMask;
import com.catcheck.scan.domain.port.ScanQueryRepository.Row;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminScanItemResponse(
        String scanId,
        String catId,
        String catName,
        String assignment,
        Instant capturedAt,
        String status,
        BigDecimal phValue,
        String classification,
        BigDecimal confidence,
        boolean nearBoundary,
        BigDecimal labL,
        BigDecimal labA,
        BigDecimal labB,
        String calibrationMethod,
        String engineVersion,
        boolean creditCharged,
        boolean trial,
        boolean disputed,
        String disputedNote,
        short reassignCount,
        Boolean imageStored
) {

    public static AdminScanItemResponse from(Row row, String catName, boolean fullData) {
        return new AdminScanItemResponse(
                row.scanId().toString(),
                row.catId() == null ? null : row.catId().toString(),
                fullData ? catName : AdminFreeTextMask.text(catName),
                row.assignment(),
                row.capturedAt(),
                row.status(),
                row.phValue(),
                row.classification(),
                row.confidence(),
                row.nearBoundary(),
                row.labL(),
                row.labA(),
                row.labB(),
                row.calibrationMethod(),
                row.engineVersion(),
                row.creditCharged(),
                row.trial(),
                row.disputedAt() != null,
                fullData ? row.disputedNote() : AdminFreeTextMask.text(row.disputedNote()),
                row.reassignCount(),
                fullData ? row.imageStored() : null);
    }
}
