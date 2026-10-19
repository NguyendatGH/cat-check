package com.catcheck.scan.api.dto;

import java.time.Instant;

public record DisputeResponse(String scanId, Instant disputedAt, boolean excludedFromTrends) {
}
