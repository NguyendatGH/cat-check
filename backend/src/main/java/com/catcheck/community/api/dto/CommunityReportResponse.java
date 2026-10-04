package com.catcheck.community.api.dto;

import com.catcheck.community.domain.CommunityReport;

import java.time.Instant;
import java.util.UUID;

public record CommunityReportResponse(UUID id, String targetType, UUID targetId, String reporterName,
                                      String reason, String details, String status, String targetTitle,
                                      Instant createdAt) {
    public static CommunityReportResponse from(CommunityReport report) {
        return new CommunityReportResponse(report.id(), report.targetType(), report.targetId(), report.reporterName(),
                report.reason(), report.details(), report.status(), report.targetTitle(), report.createdAt());
    }
}
