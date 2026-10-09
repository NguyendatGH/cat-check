package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.LinearizationCurve;
import com.catcheck.scan.domain.color.Matrix3;
import com.catcheck.scan.domain.color.port.VisionEngine;

import java.util.List;

/**
 * Cổng tiền xử lý ảnh phía scan (giải mã, crop ROI, đo chất lượng S2, hiệu chỉnh màu S4/S4b) —
 * do module {@code scan} tuyên bố (giống {@code VisionEngine}/{@code ChartCatalog}), hiện thực
 * bằng OpenCV ở {@code scan.infrastructure.vision.OpenCvRawImageProcessor}.
 *
 * <p>Tách khỏi {@link VisionEngine} (cổng của A5, KHÔNG được sửa — xem
 * {@code scan/domain/color/**}) vì đây là việc MỚI cần cho orchestration toàn pipeline (A6),
 * không phải phần S3/S6 mà {@code VisionEngine} đã phủ.</p>
 *
 * <p>Hai method {@link #sampleNeutralPatches} / {@link #applyCardCcm} là nhánh S4 CARD_CCM: lấy
 * mẫu patch trên ảnh thẻ đã nắn phẳng ({@code VisionEngine#detectCard}) rồi áp CCM đã giải cho
 * toàn ROI. {@link #calibrate} vẫn là nhánh S4b (không thẻ/không layout/CCM không dùng được) —
 * xem {@code ColorPipelineService} cho thứ tự thử và điều kiện rơi về S4b.</p>
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
     * S4b — cân bằng trắng từ nền cát (không có CCM từ thẻ) rồi nâng toàn ROI lên không
     * gian XYZ, kèm ước lượng {@code substrateLab} (S6 bước 2) trong cùng một lượt quét pixel.
     */
    CalibrationResult calibrate(VisionEngine.FrameHandle roiFrame);

    /**
     * S4 (nhánh CARD_CCM) — lấy mẫu median vùng lõi của mỗi patch {@code role=NEUTRAL} trên ảnh
     * thẻ đã nắn phẳng ({@code canonicalCard}, 600×380 theo {@code VisionEngine#detectCard}),
     * ghép với {@code ref_lab} của chính patch đó.
     *
     * <p>Giá trị trả về <b>chưa khử gamma</b> ({@code encodedR/G/B} vẫn là sRGB 8-bit đã
     * gamma-encode) vì caller cần giá trị thô này để fit {@link LinearizationCurve} trước —
     * khử gamma ở đây sẽ buộc dùng sRGB EOTF chuẩn, mất đúng thông tin mà linearization cần đo.
     * Patch nào rơi ra ngoài khung (layout hỏng) bị bỏ qua lặng lẽ; caller tự quyết định có đủ
     * patch còn lại để giải CCM hay không ({@link LinearizationCurve#MIN_GRAY_PATCHES}).
     */
    List<CardPatchSample> sampleNeutralPatches(VisionEngine.FrameHandle canonicalCard, CardLayout cardLayout);

    /**
     * S4 (nhánh CARD_CCM) — áp {@code curve} (khử gamma đo được từ thẻ) rồi {@code ccm} cho toàn
     * ROI, nâng lên XYZ, kèm ước lượng {@code substrateLab} (S6 bước 2) như {@link #calibrate}.
     * {@code residualDe00} do caller tính (ΔE00 hậu kiểm các patch NEUTRAL) và truyền thẳng vào
     * kết quả — dùng nguyên cho S9 ({@code calibrationResidualDe00Proxy}).
     */
    CalibrationResult applyCardCcm(VisionEngine.FrameHandle roiFrame, LinearizationCurve curve, Matrix3 ccm,
                                    double residualDe00);

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

    /**
     * Kết quả hiệu chỉnh (S4/S4b) + ước lượng nền S6 bước 2. {@code wbGains} = {@code [1,1,1]} và
     * {@code neutralPixelRatio} = {@code 1.0} khi {@code method = CARD_CCM} (CCM đã thay cho gain
     * đơn giản; trường này giữ nguyên nghĩa cũ ở S4b).
     */
    record CalibrationResult(
            VisionEngine.XyzFrame xyzFrame,
            CalibrationMethod method,
            double[] wbGains,
            double neutralPixelRatio,
            double calibrationResidualDe00Proxy,
            Lab substrateLab) {
    }

    /**
     * Một mẫu patch {@code role=NEUTRAL} lấy từ ảnh thẻ: median sRGB 8-bit (đã gamma-encode,
     * CHƯA khử tuyến tính) của vùng lõi patch, kèm {@code ref_lab} của chính patch — đủ để fit
     * {@link LinearizationCurve} và giải CCM (nhánh CARD_CCM, p6 §6.5.1 S4).
     */
    record CardPatchSample(String patchId, int encodedR, int encodedG, int encodedB, Lab referenceLab) {
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
