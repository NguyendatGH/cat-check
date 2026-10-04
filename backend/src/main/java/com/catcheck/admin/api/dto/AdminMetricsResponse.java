package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.AdminMetrics;

public record AdminMetricsResponse(long users, long activeCats, long scans, long failedJobs, long pendingOutbox) {
    public static AdminMetricsResponse from(AdminMetrics metrics) {
        return new AdminMetricsResponse(metrics.users(), metrics.activeCats(), metrics.scans(),
                metrics.failedJobs(), metrics.pendingOutbox());
    }
}
