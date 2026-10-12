package com.catcheck.scan.api;

import java.math.BigDecimal;
import java.time.Period;
import java.util.List;
import java.util.UUID;

public interface ChartBackfill {

    int BATCH_SIZE = 500;

    void startPreview(UUID chartId, Period window);

    BackfillImpact impact(UUID chartId);

    void startApply(UUID chartId);

    record BackfillImpact(
            long evaluated,
            long flipped,
            BigDecimal maxAbsDeltaPh,
            UUID jobId,
            List<BackfillSample> samples) {
    }

    record BackfillSample(
            UUID scanAnalysisId,
            BigDecimal newPhValue,
            String newClassification,
            BigDecimal deltaPh,
            boolean flippedClassification) {
    }
}
