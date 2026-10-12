package com.catcheck.export.api.dto;

import com.catcheck.export.domain.ExportJob;

import java.time.Instant;
import java.time.LocalDate;

public record ExportJobResponse(
        String jobId,
        String catId,
        String documentCode,
        String status,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        String rangePreset,
        Integer pageCount,
        Integer scanCount,
        int downloadCount,
        String failureReason,
        Instant requestedAt,
        Instant completedAt,
        Instant expiresAt
) {

    public static ExportJobResponse from(ExportJob job) {
        return new ExportJobResponse(
                job.getId().toString(), job.getCatId().toString(), job.getDocumentCode(),
                job.getStatus().name(), job.getRangeFrom(), job.getRangeTo(), job.getRangePreset(),
                job.getPageCount(), job.getScanCount(), job.getDownloadCount(), job.getFailureReason(),
                job.getRequestedAt(), job.getCompletedAt(), job.getExpiresAt());
    }
}
