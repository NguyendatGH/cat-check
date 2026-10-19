package com.catcheck.admin.domain;

import java.time.Instant;

public record SystemStatus(boolean databaseReachable, Instant checkedAt) {
}
