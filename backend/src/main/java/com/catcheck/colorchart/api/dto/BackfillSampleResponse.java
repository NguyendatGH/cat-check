package com.catcheck.colorchart.api.dto;

import com.catcheck.scan.api.ChartBackfill;

import java.math.BigDecimal;

/**
 * Một dòng ví dụ của L34.
 *
 * <p><b>Không</b> mang {@code scanId}, {@code catId} hay bất kỳ định danh nào dẫn về người
 * dùng: màn này để quyết định "bảng màu mới có an toàn không", không phải để xem kết quả của
 * một người cụ thể — và p11 §11.5.4 cho {@code ADMIN_CATALOG} đúng số không quyền trên dữ liệu
 * người dùng. {@code scanAnalysisId} là id dòng kỹ thuật, giữ lại để đối chiếu khi điều tra.</p>
 *
 * @param scanAnalysisId        dòng {@code scan_analysis} gốc
 * @param newPhValue            pH theo bảng mới
 * @param newClassification     phân loại theo bảng mới
 * @param deltaPh               chênh lệch so với kết quả hiện hành
 * @param flippedClassification phân loại có bị lật hay không
 */
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
