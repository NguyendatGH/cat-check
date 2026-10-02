package com.catcheck.scan.api.dto;

/** {@code POST /api/v1/scans/{id}/reassign-cat} — đúng một trong hai trường (p8 §8.5.4 E9). */
public record ReassignCatRequest(String toCatId, String toAssignment) {
}
