package com.catcheck.scan.domain;

/**
 * Danh mục mã cờ chất lượng phát sinh trong pipeline (p6 §6.3.4, §6.5.1, §6.6.2).
 *
 * <p>Không đầy đủ 16 mã mà research/p6 liệt kê cho toàn bộ pipeline card-based — chỉ những mã mà
 * {@code ColorPipelineService} thực sự sinh ra được, trên cả ba nhánh hiệu chỉnh
 * {@link CalibrationMethod#CARD_CCM}/{@link CalibrationMethod#SUBSTRATE_WB}/{@link CalibrationMethod#NONE}.
 */
public enum QualityFlagCode {
    /** Q1 — {@code blurVar} dưới ngưỡng (BLOCKING). */
    BLURRY,
    /** Q2 — {@code meanLuma} dưới ngưỡng (BLOCKING). */
    TOO_DARK,
    /** Q2 — {@code meanLuma} trên ngưỡng (BLOCKING). */
    TOO_BRIGHT,
    /** Q3 — {@code clipHigh} vượt ngưỡng (BLOCKING). */
    OVEREXPOSED,
    /** Q4 — {@code clipLow} vượt ngưỡng (WARN). */
    UNDEREXPOSED_PATCH,
    /** Q5 — ánh sáng không đều giữa các góc ROI (WARN). */
    UNEVEN_LIGHTING,
    /** S3 — không tìm thấy thẻ tham chiếu trong khung (WARN, hạ tier hiệu chỉnh). */
    CARD_NOT_FOUND,
    /**
     * S4 — thẻ tìm thấy nhưng CCM không dùng được: không có layout active, hệ CCM suy thoái, hoặc
     * phần dư hậu kiểm ΔE00 vượt {@code ScanThresholds.CARD_CCM_MAX_RESIDUAL_DE00} (WARN, rơi về
     * {@link CalibrationMethod#SUBSTRATE_WB}) — khác {@link #CARD_NOT_FOUND} (không thấy thẻ).
     */
    CARD_UNUSABLE,
    /** S4b — không đủ pixel nền trung tính để cân bằng trắng (BLOCKING, gần như luôn INCONCLUSIVE). */
    CALIBRATION_UNAVAILABLE,
    /** S6 — tỉ lệ pixel hạt chỉ thị dưới ngưỡng mềm (WARN). */
    LOW_GRANULE_COVERAGE,
    /** S6 — tỉ lệ pixel hạt chỉ thị dưới ngưỡng cứng (BLOCKING). */
    NO_INDICATOR_GRAINS,
    /** S7 — số hạt giữ lại sau lọc outlier dưới {@code minBlobs} (WARN). */
    FEW_BLOBS,
    /** S7 — độ phân tán màu giữa các hạt vượt ngưỡng (WARN). */
    MIXED_COLORS,
    /** S8 — màu đo được nằm ngoài dải bảng màu, đã kẹp về đầu mút (WARN). */
    OUT_OF_CHART_RANGE
}
