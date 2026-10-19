package com.catcheck.privacy.api.dto;

import java.time.Instant;
import java.util.List;

public record PolicyVersionSummaryView(
        String version,
        String locale,
        String title,
        Instant effectiveFrom,
        Instant effectiveTo,
        boolean current,
        boolean requiresReconsent,
        String summaryOfChanges,
        List<String> affectedPurposes
) {
}
