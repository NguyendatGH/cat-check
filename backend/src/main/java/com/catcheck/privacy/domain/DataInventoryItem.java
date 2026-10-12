package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.List;

public record DataInventoryItem(
        String code,
        String categoryVi,
        String descriptionVi,
        String descriptionEn,
        InventorySensitivity sensitivity,
        LegalBasis legalBasis,
        List<String> purposeCodes,
        String retentionPolicyCode,
        String storageLocation,
        boolean crossBorder,
        String recipient,
        boolean active,
        Instant createdAt,
        Integer retentionDays
) {

    public DataInventoryItem {
        if (code == null || categoryVi == null || descriptionVi == null) {
            throw new IllegalArgumentException("dataInventoryItem thiếu trường bắt buộc");
        }
        if (sensitivity == null || legalBasis == null || storageLocation == null) {
            throw new IllegalArgumentException(
                    "dataInventoryItem bắt buộc có sensitivity, legalBasis, storageLocation (p15 §15.4.3)");
        }
        purposeCodes = purposeCodes == null ? List.of() : List.copyOf(purposeCodes);
    }
}
