package com.catcheck.scan.domain.color;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tập cặp mẫu dùng để ước lượng CCM: mỗi mẫu là một ô màu đã đo (linear RGB) và giá trị tham chiếu
 * của nó (linear RGB), kèm ước lượng nhiễu.
 *
 * <p>Giá trị tham chiếu phải là <b>linear</b> chứ không phải sRGB 8-bit: vì hệ phương trình CCM là
 * tuyến tính, mà chỉ tuyến tính trong không gian linear. Cho 8-bit vào đây sẽ cho ra một CCM "đúng
 * theo nghĩa bậc hai" mà sai về màu.
 *
 * @param samples các cặp mẫu
 */
public record ColorSamples(List<Sample> samples) {

    public ColorSamples {
        if (samples == null) {
            throw new IllegalArgumentException("Danh sach mau khong duoc null");
        }
        samples = List.copyOf(samples);
    }

    public static ColorSamples of(Collection<Sample> samples) {
        return new ColorSamples(new ArrayList<>(samples));
    }

    public int size() {
        return samples.size();
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    /**
     * Một cặp mẫu.
     *
     * @param measured  màu đo được từ ảnh, đã khử gamma
     * @param reference màu tham chiếu đúng, đã khử gamma
     * @param sigma     ước lượng độ lệch chuẩn của mẫu này (linear). Với mẫu nhiễu đồng nhất
     *                  {@code sigma = 1/255} là mặc định của kiểm thử (p6 §6.13.2)
     * @param weight    trọng số tương đối; 0 nghĩa là loại mẫu này khỏi phép khớp
     */
    public record Sample(RgbLinear measured, RgbLinear reference, double sigma, double weight) {

        public Sample {
            requireFinite(measured, "measured");
            requireFinite(reference, "reference");
            if (!(sigma > 0.0) || Double.isNaN(sigma)) {
                throw new IllegalArgumentException("sigma phai > 0, nhan duoc " + sigma);
            }
            if (weight < 0.0 || Double.isNaN(weight)) {
                throw new IllegalArgumentException("weight phai >= 0, nhan duoc " + weight);
            }
        }

        /** Mẫu nhiễu đồng nhất ở mức 1/255 — mặc định của bài kiểm thử. */
        public static Sample of(RgbLinear measured, RgbLinear reference) {
            return new Sample(measured, reference, 1.0 / 255.0, 1.0);
        }

        /** Mẫu bị loại khỏi phép khớp (ví dụ nằm ngoài vùng card hợp lệ). */
        public static Sample excluded(RgbLinear measured, RgbLinear reference) {
            return new Sample(measured, reference, 1.0 / 255.0, 0.0);
        }

        /** Trọng số theo phương sai: {@code w = 1/σ²} (nhân {@code 255²} để giữ số thuận tiện). */
        public double weightFromSigma() {
            return 1.0 / (sigma * sigma);
        }
    }

    private static void requireFinite(RgbLinear rgb, String name) {
        if (rgb == null) {
            throw new IllegalArgumentException("Mau " + name + " khong duoc null");
        }
        if (Double.isNaN(rgb.r()) || Double.isNaN(rgb.g()) || Double.isNaN(rgb.b())) {
            throw new IllegalArgumentException("Mau " + name + " chua NaN");
        }
    }
}
