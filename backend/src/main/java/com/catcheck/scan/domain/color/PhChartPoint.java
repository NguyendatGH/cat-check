package com.catcheck.scan.domain.color;

/**
 * Một mức pH trên bảng màu — ánh xạ phía scan của {@code color_chart_point} (p4 §4.9).
 *
 * @param id              UUID mức
 * @param phValue         giá trị pH (một số cấu hình, không cố định)
 * @param lab             màu tham chiếu CIELAB D65/2° — <b>nguồn sự thật cho phép khớp</b>
 * @param toleranceDeltaE ngưỡng ΔE00 chấp nhận cho mức này (mặc định publish: 4.0)
 * @param hexSrgb         màu admin nhập, có thể {@code null}
 * @param displayHex      màu vẽ swatch UI, có thể khác {@code hexSrgb}
 * @param displayNameVi   tên hiển thị tiếng Việt (QĐ #15)
 * @param displayNameEn   tên hiển thị tiếng Anh
 * @param sampleCount     số ảnh mẫu đã dùng khi calibrate
 * @param spreadDeltaE    độ phân tán ΔE00 khi calibrate
 */
public record PhChartPoint(
        String id,
        double phValue,
        Lab lab,
        double toleranceDeltaE,
        String hexSrgb,
        String displayHex,
        String displayNameVi,
        String displayNameEn,
        int sampleCount,
        double spreadDeltaE) {

    /** Ngưỡng mặc định khi publish (p6 §6.6.3). */
    public static final double DEFAULT_TOLERANCE = 4.0;

    /** Khoảng cách ΔE00 (kL=2) giữa mức này và mức kế — ràng buộc publish là ≥ 3. */
    public double deltaETo(PhChartPoint other, DeltaE2000Params params) {
        return DeltaE2000.deltaE(lab, other.lab, params);
    }

    /** Màu vẽ trên UI: ưu tiên {@code displayHex}, không có thì tính lại từ Lab. */
    public String swatchHex() {
        return displayHex != null ? displayHex : ColorSpace.labToHex(lab);
    }
}
