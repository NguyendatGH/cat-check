package com.catcheck.scan.domain.color;

/**
 * Cân bằng trắng von Kries trong không gian linear (nghiên cứu color-pipeline S4, p6 S4).
 *
 * <h2>Tại sao là von Kries mà không phải chia tỉ lệ thẳng</h2>
 * <p>Ánh sáng trung tính phải được đưa về trung tính. Cách tối giản — chia mỗi kênh cho giá trị
 * của nó trong vùng trắng — là xấp xỉ của phép nhân, và chỉ đúng khi phổ đáp ứng gần phẳng. Von Kries
 * chuẩn hoá theo một điểm tham chiếu {@code (R_w, G_w, B_w)} rồi chia:
 *
 * <pre>
 *   R' = R · (R_w / R_w) = R   — với điểm tham chiếu chính nó, phép này là đẳng nhất
 * </pre>
 *
 * <p>Ở đây điểm tham chiếu là <b>trung bình các ô trung tính trên thẻ</b> hoặc p95 vùng trắng từ
 * bề mặt. Hệ số thu được luôn được kiểm tra {@code gain} nằm trong dải hợp lý: một ô trung tính bị
 * bóng đổ nhẹ có thể kéo {@code gain} lên 2–3 lần và biến ảnh bình thường thành ảnh đầy màu.
 * Vì vậy có {@link #MAX_GAIN} và phép kẹp — sửa ảnh hỏng còn hơn làm hỏng ảnh tốt.
 */
public final class WhiteBalanceSolver {

    /** Trần cho mỗi gain: vượt ngưỡng này là thẻ bị loá, không phải ảnh cần sửa. */
    public static final double MAX_GAIN = 3.0;

    /** Sàn cho mỗi gain. */
    public static final double MIN_GAIN = 1.0 / MAX_GAIN;

    private WhiteBalanceSolver() {
        throw new AssertionError("WhiteBalanceSolver la lop tien ich, khong instantiate");
    }

    /** Hệ số cân bằng trắng. */
    public record Gains(double r, double g, double b) {

        /** Áp lên một màu linear. */
        public RgbLinear apply(RgbLinear rgb) {
            return rgb.times(r, g, b);
        }

        /** Mảng 3 phần tử để serialize vào {@code scan_analysis}. */
        public double[] toArray() {
            return new double[] {r, g, b};
        }
    }

    /**
     * Tính gain từ điểm tham chiếu trung tính.
     *
     * @param neutralPoint màu linear của vùng trắng (trung bình ô trung tính hoặc p95 nền)
     */
    public static Gains fromNeutralPoint(RgbLinear neutralPoint) {
        double scale = neutralPoint.mean();
        if (scale <= 1e-6) {
            throw new IllegalArgumentException("Diem tham chieu qua toi, khong the can bang trang");
        }
        return new Gains(
                clampGain(scale / neutralPoint.r()),
                clampGain(scale / neutralPoint.g()),
                clampGain(scale / neutralPoint.b()));
    }

    /**
     * Nhánh dự phòng S4b: pha trộn <b>50/50 gray-world</b> với <b>white-patch p95</b>.
     *
     * <p>Vì sao pha trộn chứ không chọn một trong hai: gray-world hỏng khi cát chiếm phần lớn khung hình
     * và màu của cát trùng màu chỉ thị; white-patch hỏng khi ảnh thiếu vùng trắng thật (đèn vàng, bóng
     * râm). Trung bình đôi luôn cho kết quả tệ hơn đỉnh tốt nhưng không bao giờ thảm họa — và nhánh
     * này vốn đã bị trần confidence 0.60.
     */
    public static Gains fallback(java.util.List<RgbLinear> backgroundPixels) {
        if (backgroundPixels == null || backgroundPixels.isEmpty()) {
            throw new IllegalArgumentException("Khong co pixel nen de uoc luong can bang trang");
        }
        RgbLinear grayWorld = grayWorld(backgroundPixels);
        RgbLinear whitePatch = whitePatch(backgroundPixels);
        RgbLinear blended = new RgbLinear(
                0.5 * (grayWorld.r() + whitePatch.r()),
                0.5 * (grayWorld.g() + whitePatch.g()),
                0.5 * (grayWorld.b() + whitePatch.b()));
        return fromNeutralPoint(blended);
    }

    /** Gray-world: trung bình ba kênh của toàn vùng. */
    public static RgbLinear grayWorld(java.util.List<RgbLinear> pixels) {
        double sr = 0.0;
        double sg = 0.0;
        double sb = 0.0;
        for (RgbLinear pixel : pixels) {
            sr += pixel.r();
            sg += pixel.g();
            sb += pixel.b();
        }
        int n = pixels.size();
        return new RgbLinear(sr / n, sg / n, sb / n);
    }

    /** White-patch: giá trị gần trắng nhất (p95 theo độ sáng), ấn bằng mean để không bị hạt sáng lóa kéo đi. */
    public static RgbLinear whitePatch(java.util.List<RgbLinear> pixels) {
        java.util.List<RgbLinear> sorted = new java.util.ArrayList<>(pixels);
        sorted.sort(java.util.Comparator.comparingDouble(RgbLinear::mean).reversed());
        int take = Math.max(1, (int) Math.floor(sorted.size() * 0.05));
        double sr = 0.0;
        double sg = 0.0;
        double sb = 0.0;
        for (int i = 0; i < take; i++) {
            sr += sorted.get(i).r();
            sg += sorted.get(i).g();
            sb += sorted.get(i).b();
        }
        return new RgbLinear(sr / take, sg / take, sb / take);
    }

    private static double clampGain(double gain) {
        if (Double.isNaN(gain) || Double.isInfinite(gain)) {
            return 1.0;
        }
        return Math.max(MIN_GAIN, Math.min(MAX_GAIN, gain));
    }
}
