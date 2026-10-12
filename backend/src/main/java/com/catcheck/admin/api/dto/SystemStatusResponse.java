package com.catcheck.admin.api.dto;

import java.time.Instant;

public record SystemStatusResponse(boolean databaseReachable, Instant checkedAt) {
}
