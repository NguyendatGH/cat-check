package com.catcheck.admin.domain;

/** Các số liệu tổng hợp chỉ đọc cho dashboard vận hành admin. */
public record AdminMetrics(long users, long activeCats, long scans, long failedJobs, long pendingOutbox) {
}
