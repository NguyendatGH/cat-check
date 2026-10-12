package com.catcheck.scan.api.dto;

import java.util.List;

public record ReassignCatResponse(
        String scanId,
        String fromCatId,
        String toCatId,
        int reassignRemaining,
        List<String> revokedFlagIds,
        List<String> recomputedCats
) {
}
