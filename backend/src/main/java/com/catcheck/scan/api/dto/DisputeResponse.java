package com.catcheck.scan.api.dto;

import java.time.Instant;

/** {@code POST /api/v1/scans/{id}/dispute} (p8 §8.5.4 E10). */
public record DisputeResponse(String scanId, Instant disputedAt, boolean excludedFromTrends) {
}
