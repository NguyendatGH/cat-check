package com.catcheck.ai.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatContextPort {

    Optional<CatContext> loadContext(UUID userId, UUID catId);

    record CatContext(
            UUID catId,
            String name,
            Integer ageMonths,
            String breedCode,
            String breedOther,
            String sex,
            Boolean neutered,
            BigDecimal weightKg,
            Instant weightUpdatedAt,
            LatestScanSummary latestScan,
            TrendSummary last30Days,
            List<OpenHealthFlag> openHealthFlags,
            List<RecentClinicalSign> recentClinicalSigns,
            SurveySummary survey,
            List<ActiveReminder> activeReminders,
            CreditSnapshot credit) {

        public CatContext {
            openHealthFlags = openHealthFlags == null ? List.of() : List.copyOf(openHealthFlags);
            recentClinicalSigns = recentClinicalSigns == null ? List.of() : List.copyOf(recentClinicalSigns);
            activeReminders = activeReminders == null ? List.of() : List.copyOf(activeReminders);
        }
    }

    record LatestScanSummary(
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            String calibrationMethod,
            boolean nearBoundary) {
    }

    record TrendSummary(int scanCount, BigDecimal medianPh, BigDecimal inRangeRatio) {
    }

    record OpenHealthFlag(String ruleCode, String severity, Instant triggeredAt) {
    }

    record RecentClinicalSign(List<String> signs, Instant reportedAt, boolean acknowledged) {
    }

    record SurveySummary(
            String litterType,
            String urinaryHistory,
            String dietType,
            String urinationFrequency,
            List<String> symptoms) {
    }

    record ActiveReminder(String type, Instant nextRunAt) {
    }

    record CreditSnapshot(
            boolean hasAvailableCredit,
            boolean hasTrialScanRemaining,
            boolean hasWriteAccess,
            Integer maxCatProfiles) {
    }
}
