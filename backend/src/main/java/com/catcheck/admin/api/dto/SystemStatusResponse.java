package com.catcheck.admin.api.dto;

import java.time.Instant;

/**
 * DTO trả về của {@code GET /api/v1/admin/system-status}. Mọi DTO trong {@code ..api.dto..}
 * phải là {@code record} và không mang annotation JPA (ArchUnit R14).
 */
public record SystemStatusResponse(boolean databaseReachable, Instant checkedAt) {
}
