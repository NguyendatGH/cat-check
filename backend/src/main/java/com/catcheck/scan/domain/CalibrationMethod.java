package com.catcheck.scan.domain;

/**
 * Phương pháp hiệu chỉnh màu đã dùng cho một kết quả — cột {@code scan_analysis.calibration_method}
 * (p4 D3, p6 §6.5.1 S4/S4b).
 *
 * <p>MVP (M3, xem {@code docs/handovers/A6.md}) chỉ hiện thực nhánh {@link #SUBSTRATE_WB} /
 * {@link #NONE}: {@link #CARD_CCM} cần toạ độ patch của {@code reference_card_layout}, mà cổng
 * {@code ChartCatalog} (do module {@code scan} tuyên bố, {@code colorchart} hiện thực) chưa công
 * bố dữ liệu đó — mở rộng cổng đòi sửa {@code colorchart} (ngoài phạm vi module sở hữu của A6).
 * Giá trị này vẫn được định nghĩa đủ 3 mức theo đúng {@code CHECK} của p4 để không phải sửa lại
 * khi cổng được mở rộng ở M7.
 */
public enum CalibrationMethod {
    /** Có thẻ tham chiếu + CCM 3x3 đã ước lượng từ patch trên thẻ. Trần confidence 0.95 (tier A). */
    CARD_CCM,
    /** Không có thẻ hoặc không tính CCM được — cân bằng trắng từ nền cát. Trần 0.60 (tier B). */
    SUBSTRATE_WB,
    /** Không hiệu chỉnh được (không đủ pixel nền trung tính). Trần 0.35 (tier C) — gần như luôn INCONCLUSIVE. */
    NONE
}
