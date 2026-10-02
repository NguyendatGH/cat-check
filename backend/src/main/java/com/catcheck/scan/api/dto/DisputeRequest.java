package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/scans/{id}/dispute} (p8 §8.5.4 E10). */
public record DisputeRequest(@Size(max = 500) String note) {
}
