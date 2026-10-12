package com.catcheck.privacy.api.dto;

import java.time.Instant;
import java.util.List;

public record PolicyView(
        String policyType,
        String version,
        String locale,
        String title,
        String contentMd,
        String contentUrl,
        Instant effectiveFrom,
        boolean requiresReconsent,
        String summaryOfChanges,
        List<String> affectedPurposes
) {
}
