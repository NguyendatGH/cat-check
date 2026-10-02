package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.port.VisionEngine;

/**
 * Cổng tiền xử lý ảnh phía scan (giải mã, crop ROI, đo chất lượng S2, hiệu chỉnh màu S4/S4b) —
 * do module {@code scan} tuyên bố (giống {@code VisionEngine}/{@code ChartCatalog}), hiện thực
 * bằng OpenCV ở {@code scan.infrastructure.vision.OpenCvRawImageProcessor}.
 *
 * <p>Tách khỏi {@link VisionEngine} (cổng của A5, KHÔNG được sửa — xem
 * {@code scan/domain/color/**}) vì đây là việc MỚI cần cho orchestration toàn pipeline (A6),
 * không phải phần S3/S6 mà {@code VisionEngine} đã phủ.</p>
 *
 * <p><b>Phạm vi MVP (M3):</b> chỉ hiện thực nhánh S4b (không CCM từ patch thẻ) — xem
 * {@link CalibrationMethod} javadoc và {@code docs/handovers/A6.md}.</p>
 */
public interface RawImagePort {

    /**
     * Giải mã byte ảnh, sniff định dạng bằng magic bytes (KHÔNG tin {@code Content-Type} của
     * client, p6 §6.4.1).
     *
     * @throws UnsupportedFormatException định dạng không phải jpeg/png/webp
     * @throws ImageDecodeException       không giải mã được (file hỏng)
     */
    DecodedImage decode(byte[] bytes);

    /** {@code false} nếu OpenCV native không nạp được — dùng để trả {@code VISION_ENGINE_UNAVAILABLE}. */
    boolean isAvailable();

    /** S2 — đo lại chất lượng server-side, không tin metric client (p6 §6.3.4, §6.5.1 S2). */
    QualityMetrics measureQuality(VisionEngine.FrameHandle frame);

    /** Cắt về vùng quan tâm theo toạ độ chuẩn hoá {@code [0,1]} client gửi kèm metadata. */
    VisionEngine.FrameHandle cropToRoi(VisionEngine.FrameHandle frame, RoiRect roi);

    /**
     * S4b — cân bằng trắng từ nền cát (không có CCM từ thẻ ở MVP) rồi nâng toàn ROI lên không
     * gian XYZ, kèm ước lượng {@code substrateLab} (S6 bước 2) trong cùng một lượt quét pixel.
     */
    CalibrationResult calibrate(VisionEngine.FrameHandle roiFrame);

    /** Ảnh đã giải mã. */
    record DecodedImage(VisionEngine.FrameHandle frame, int width, int height, String contentType) {
    }

    /** Vùng quan tâm chuẩn hoá {@code [0,1]}, gốc trên-trái (p6 §6.3.5). */
    record RoiRect(double x, double y, double w, double h) {

        public RoiRect {
            if (w <= 0 || h <= 0) {
                throw new IllegalArgumentException("roi.w/h phai > 0");
            }
        }

        public static RoiRect fullFrame() {
            return new RoiRect(0.0, 0.0, 1.0, 1.0);
        }
    }

    /** Chỉ số chất lượng S2 (p6 §6.3.4 Q1-Q5), đo lại server-side trên ROI. */
    record QualityMetrics(
            double blurVariance,
            double meanLuma,
            double clipHigh,
            double clipLow,
            double lumaGradient) {
    }

    /** Kết quả hiệu chỉnh S4b + ước lượng nền S6 bước 2. */
    record CalibrationResult(
            VisionEngine.XyzFrame xyzFrame,
            CalibrationMethod method,
            double[] wbGains,
            double neutralPixelRatio,
            double calibrationResidualDe00Proxy,
            Lab substrateLab) {
    }

    class UnsupportedFormatException extends RuntimeException {
        public UnsupportedFormatException(String message) {
            super(message);
        }
    }

    class ImageDecodeException extends RuntimeException {
        public ImageDecodeException(String message) {
            super(message);
        }

        public ImageDecodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
