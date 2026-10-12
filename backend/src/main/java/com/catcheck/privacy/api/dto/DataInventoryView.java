package com.catcheck.privacy.api.dto;

import java.util.List;

public record DataInventoryView(
        String code,
        String category,
        String description,
        String sensitivity,
        String legalBasis,
        List<String> purposes,
        String retentionPolicyCode,
        String storageLocation,
        boolean crossBorder,
        String recipient,
        Integer retentionDays
) {
}
