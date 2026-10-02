package com.catcheck.admin.domain;

import java.time.Instant;

/**
 * Domain thuần Java (không annotation Spring/JPA) — ví dụ minh hoạ cho layer "domain" của
 * kiến trúc 4 tầng {@code api -> application -> domain <- infrastructure}. Dùng để chứng minh
 * toàn bộ khung module chạy được xuyên suốt ở M0 (yêu cầu nhiệm vụ: chọn shared + một module
 * nghiệp vụ tối giản, ví dụ health-check xuyên suốt).
 */
public record SystemStatus(boolean databaseReachable, Instant checkedAt) {
}
