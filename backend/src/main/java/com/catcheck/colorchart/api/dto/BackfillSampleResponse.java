package com.catcheck.colorchart.api.dto;

import com.catcheck.scan.api.ChartBackfill;

import java.math.BigDecimal;

public record BackfillSampleResponse(
        String scanAnalysisId,
        BigDecimal newPhValue,
        String newClassification,
        BigDecimal deltaPh,
        Boolean flippedClassification
) {

    public static BackfillSampleResponse from(ChartBackfill.BackfillSample sample) {
        return new BackfillSampleResponse(
                sample.scanAnalysisId().toString(),
                sample.newPhValue(),
                sample.newClassification(),
                sample.deltaPh(),
                sample.flippedClassification());
    }
}
