package com.catcheck.colorchart.api.dto;

import com.catcheck.scan.api.ChartBackfill;

import java.math.BigDecimal;
import java.util.List;

public record BackfillPreviewResponse(
        String chartId,
        String jobId,
        Long evaluated,
        Long flipped,
        BigDecimal maxAbsDeltaPh,
        List<BackfillSampleResponse> samples
) {

    public static BackfillPreviewResponse from(String chartId, ChartBackfill.BackfillImpact impact) {
        return new BackfillPreviewResponse(
                chartId,
                impact.jobId() == null ? null : impact.jobId().toString(),
                impact.evaluated(),
                impact.flipped(),
                impact.maxAbsDeltaPh(),
                impact.samples().stream().map(BackfillSampleResponse::from).toList());
    }
}
