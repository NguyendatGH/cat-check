package com.catcheck.admin.domain;

public record AdminMetrics(long users, long activeCats, long scans, long failedJobs, long pendingOutbox) {
}
