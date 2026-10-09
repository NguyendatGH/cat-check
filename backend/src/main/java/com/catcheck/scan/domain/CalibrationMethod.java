package com.catcheck.scan.domain;

/**
 * Phương pháp hiệu chỉnh màu đã dùng cho một kết quả — cột {@code scan_analysis.calibration_method}
 * (p4 D3, p6 §6.5.1 S4/S4b).
 *
 * <p>Cả ba mức đều được hiện thực thật trong {@code ColorPipelineService}: khi thẻ tham chiếu
 * được dò thấy VÀ layout của nó đang active (cổng {@code CardLayoutCatalog}, do module
 * {@code scan} tuyên bố, {@code colorchart} hiện thực bằng {@code reference_card_layout}), pipeline
 * fit linearization + giải CCM 3×3 từ các patch {@code role=NEUTRAL} trên thẻ. Phần dư hậu kiểm
 * (ΔE00 so với {@code ref_lab}) phải đủ tốt mới dùng {@link #CARD_CCM} — không thì rơi êm về
 * {@link #SUBSTRATE_WB} (xem {@code ScanThresholds.CARD_CCM_MAX_RESIDUAL_DE00} và cờ
 * {@code QualityFlagCode.CARD_UNUSABLE}).
 */
public enum CalibrationMethod {
    /** Có thẻ tham chiếu + CCM 3x3 đã ước lượng từ patch trên thẻ. Trần confidence 0.95 (tier A). */
    CARD_CCM,
    /** Không có thẻ hoặc không tính CCM được — cân bằng trắng từ nền cát. Trần 0.60 (tier B). */
    SUBSTRATE_WB,
    /** Không hiệu chỉnh được (không đủ pixel nền trung tính). Trần 0.35 (tier C) — gần như luôn INCONCLUSIVE. */
    NONE
}
