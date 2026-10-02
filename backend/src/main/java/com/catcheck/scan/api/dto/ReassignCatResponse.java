package com.catcheck.scan.api.dto;

import java.util.List;

/** {@code POST /api/v1/scans/{id}/reassign-cat} (p8 §8.5.4 E9). */
public record ReassignCatResponse(
        String scanId,
        String fromCatId,
        String toCatId,
        int reassignRemaining,
        List<String> revokedFlagIds,
        List<String> recomputedCats
) {
}
