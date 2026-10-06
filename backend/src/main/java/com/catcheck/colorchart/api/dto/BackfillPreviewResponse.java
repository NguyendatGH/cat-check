package com.catcheck.colorchart.api.dto;

import com.catcheck.scan.api.ChartBackfill;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body của L34 — "số bản ghi bị <b>lật phân loại</b>, {@code deltaPh} lớn nhất" (p8 §8.4.12 L34).
 *
 * <p>{@code evaluated} đi kèm hai con số kia vì không có mẫu số thì "12 bản ghi bị lật" không
 * nói được gì: 12 trên 20 và 12 trên 40.000 là hai quyết định khác nhau.</p>
 *
 * @param chartId       bảng màu đã tính thử
 * @param jobId         {@code job_run.id} của lượt preview; {@code null} khi chưa chạy
 * @param evaluated     số bản ghi đã tính thử
 * @param flipped       số bản ghi bị lật phân loại
 * @param maxAbsDeltaPh {@code |deltaPh|} lớn nhất; {@code null} khi chưa có dòng nào
 * @param samples       vài dòng lệch nhiều nhất, để thấy hình dạng thay đổi
 */
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
