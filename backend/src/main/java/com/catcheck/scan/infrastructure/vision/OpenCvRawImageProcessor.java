package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.RgbLinear;
import com.catcheck.scan.domain.color.RobustStats;
import com.catcheck.scan.domain.color.WhiteBalanceSolver;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hiện thực {@link RawImagePort} bằng OpenCV — giải mã, crop ROI, đo chất lượng (S2), hiệu chỉnh
 * màu nhánh S4b (không CCM từ thẻ ở MVP, xem {@code docs/handovers/A6.md}).
 *
 * <p>Cùng nguyên tắc bộ nhớ với {@link OpenCvVisionEngine}: mọi {@link Mat} trong
 * try-with-resources.</p>
 */
@Component
public class OpenCvRawImageProcessor implements RawImagePort {

    private static final Logger log = LoggerFactory.getLogger(OpenCvRawImageProcessor.class);

    /** Bước lấy mẫu khi ước lượng gain cân bằng trắng — không cần quét từng pixel để đủ chính xác. */
    private static final int NEUTRAL_SAMPLE_STRIDE = 3;

    /** Cạnh dài tối đa của ảnh đưa vào hiệu chỉnh + tách hạt (px); ảnh lớn hơn được thu nhỏ INTER_AREA. */
    static final int ANALYSIS_MAX_EDGE = 1280;

    private final OpenCvNativeLoader nativeLoader;

    public OpenCvRawImageProcessor(OpenCvNativeLoader nativeLoader) {
        this.nativeLoader = nativeLoader;
    }

    @Override
    public boolean isAvailable() {
        return nativeLoader.isAvailable();
    }

    @Override
    public DecodedImage decode(byte[] bytes) {
        String contentType = sniffContentType(bytes);
        if (contentType == null) {
            throw new UnsupportedFormatException("Khong nhan dien duoc dinh dang anh tu magic bytes");
        }
        if (!nativeLoader.isAvailable()) {
            throw new ImageDecodeException("OpenCV native khong kha dung — xem /actuator/health/vision");
        }
        try (Mat encoded = new Mat(1, bytes.length, opencv_core.CV_8UC1)) {
            encoded.data().put(bytes);
            Mat decoded = opencv_imgcodecs.imdecode(encoded, opencv_imgcodecs.IMREAD_COLOR);
            if (decoded == null || decoded.empty()) {
                throw new ImageDecodeException("imdecode tra ve Mat rong — file anh hong");
            }
            return new DecodedImage(new OpenCvFrameHandle(decoded), decoded.cols(), decoded.rows(), contentType);
        } catch (ImageDecodeException | UnsupportedFormatException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new ImageDecodeException("Loi khi giai ma anh: " + ex.getMessage(), ex);
        }
    }

    /** Sniff magic bytes — KHÔNG tin {@code Content-Type} do client khai (p6 §6.4.1). */
    private String sniffContentType(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return "image/png";
        }
        boolean riff = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F';
        boolean webp = b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        if (riff && webp) {
            return "image/webp";
        }
        return null;
    }

    @Override
    public VisionEngine.FrameHandle cropToRoi(VisionEngine.FrameHandle frame, RoiRect roi) {
        if (!(frame instanceof OpenCvFrameHandle handle)) {
            throw new IllegalArgumentException("Phai dung OpenCvFrameHandle");
        }
        {
            // `handle` sở hữu Mat: KHÔNG đóng ở đây (try-with-resources làm Mat bị giải phóng sau lần dùng đầu,
        // các bước sau của pipeline gặp con trỏ NULL).
            Mat source = handle.mat();
            int width = source.cols();
            int height = source.rows();
            int x = clamp((int) Math.round(roi.x() * width), 0, width - 1);
            int y = clamp((int) Math.round(roi.y() * height), 0, height - 1);
            int w = clamp((int) Math.round(roi.w() * width), 1, width - x);
            int h = clamp((int) Math.round(roi.h() * height), 1, height - y);
            Mat cropped = new Mat(source, new Rect(x, y, w, h)).clone();
            return new OpenCvFrameHandle(cropped);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public QualityMetrics measureQuality(VisionEngine.FrameHandle frame) {
        if (!(frame instanceof OpenCvFrameHandle handle)) {
            throw new IllegalArgumentException("Phai dung OpenCvFrameHandle");
        }
        // `handle` sở hữu Mat nguồn: không đưa vào try-with-resources (xem `cropToRoi`).
        final Mat source = handle.mat();
        try (Mat gray = new Mat();
             Mat laplacian = new Mat();
             Mat mean = new Mat();
             Mat stddev = new Mat()) {

            opencv_imgproc.cvtColor(source, gray, opencv_imgproc.COLOR_BGR2GRAY);

            // Q1 — do net bang phuong sai Laplacian (chuan cong nghiep, khong phai cong thuc p6).
            opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);
            opencv_core.meanStdDev(laplacian, mean, stddev);
            double blurVariance = Math.pow(new DoublePointer(stddev.ptr()).get(0), 2);

            // Q2 — do sang trung binh.
            double meanLuma = opencv_core.mean(gray).get(0);

            int total = gray.rows() * gray.cols();
            int clipHighCount = countAboveOrBelow(gray, 250, true);
            int clipLowCount = countAboveOrBelow(gray, 5, false);
            double clipHigh = total == 0 ? 0.0 : (double) clipHighCount / total;
            double clipLow = total == 0 ? 0.0 : (double) clipLowCount / total;

            // Q5 — do deu sang: chenh lech giua trung binh 4 goc ROI.
            double lumaGradient = quadrantLumaGradient(gray);

            return new QualityMetrics(blurVariance, meanLuma, clipHigh, clipLow, lumaGradient);
        }
    }

    private int countAboveOrBelow(Mat gray, int threshold, boolean above) {
        try (Mat mask = new Mat()) {
            if (above) {
                opencv_imgproc.threshold(gray, mask, threshold, 255, opencv_imgproc.THRESH_BINARY);
            } else {
                opencv_imgproc.threshold(gray, mask, threshold, 255, opencv_imgproc.THRESH_BINARY_INV);
            }
            return opencv_core.countNonZero(mask);
        }
    }

    private double quadrantLumaGradient(Mat gray) {
        int w = gray.cols();
        int h = gray.rows();
        int halfW = Math.max(1, w / 2);
        int halfH = Math.max(1, h / 2);
        double[] means = new double[4];
        try (Mat q1 = new Mat(gray, new Rect(0, 0, halfW, halfH));
             Mat q2 = new Mat(gray, new Rect(w - halfW, 0, halfW, halfH));
             Mat q3 = new Mat(gray, new Rect(0, h - halfH, halfW, halfH));
             Mat q4 = new Mat(gray, new Rect(w - halfW, h - halfH, halfW, halfH))) {
            means[0] = opencv_core.mean(q1).get(0);
            means[1] = opencv_core.mean(q2).get(0);
            means[2] = opencv_core.mean(q3).get(0);
            means[3] = opencv_core.mean(q4).get(0);
        }
        double max = means[0];
        double min = means[0];
        for (double m : means) {
            max = Math.max(max, m);
            min = Math.min(min, m);
        }
        return max - min;
    }

    @Override
    public CalibrationResult calibrate(VisionEngine.FrameHandle roiFrame) {
        if (!(roiFrame instanceof OpenCvFrameHandle handle)) {
            throw new IllegalArgumentException("Phai dung OpenCvFrameHandle");
        }
        // `handle` sở hữu Mat: KHÔNG đóng ở đây (try-with-resources làm Mat bị giải phóng sau lần dùng đầu,
        // các bước sau của pipeline gặp con trỏ NULL).
        Mat source = handle.mat();
        Mat scaled = null;
        try {
            // Phân tích ở độ phân giải vừa đủ: ảnh điện thoại ~12MP không cần quét từng pixel gốc.
            int longEdge = Math.max(source.cols(), source.rows());
            Mat work = source;
            if (longEdge > ANALYSIS_MAX_EDGE) {
                double scale = (double) ANALYSIS_MAX_EDGE / longEdge;
                scaled = new Mat();
                opencv_imgproc.resize(source, scaled,
                        new Size(Math.max(1, (int) Math.round(source.cols() * scale)),
                                Math.max(1, (int) Math.round(source.rows() * scale))),
                        0, 0, opencv_imgproc.INTER_AREA);
                work = scaled;
            }
            return calibrateOn(work);
        } finally {
            if (scaled != null) {
                scaled.close();
            }
        }
    }

    /** Bảng tra sRGB 8-bit → linear (cùng công thức {@link ColorSpace#srgb8ToLinear(int)}). */
    private static final double[] LINEAR_LUT = buildLut();

    private static double[] buildLut() {
        double[] lut = new double[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = ColorSpace.srgb8ToLinear(i);
        }
        return lut;
    }

    private CalibrationResult calibrateOn(Mat source) {
        int width = source.cols();
        int height = source.rows();
        // Đọc cả ảnh BGR một lần (Mat liên tục sau decode/clone/resize).
        Mat contiguous = source.isContinuous() ? source : source.clone();
        byte[] bgr = new byte[width * height * 3];
        try {
            contiguous.data().get(bgr);
        } finally {
            if (contiguous != source) {
                contiguous.close();
            }
        }

        // --- Buoc 1: lay mau tim pixel "nen trung tinh" (S4b) de uoc luong gain.
        List<RgbLinear> neutralSamples = new ArrayList<>();
        long sampledCount = 0;
        for (int y = 0; y < height; y += NEUTRAL_SAMPLE_STRIDE) {
            for (int x = 0; x < width; x += NEUTRAL_SAMPLE_STRIDE) {
                sampledCount++;
                int i = (y * width + x) * 3;
                RgbLinear linear = new RgbLinear(LINEAR_LUT[bgr[i + 2] & 0xFF],
                        LINEAR_LUT[bgr[i + 1] & 0xFF], LINEAR_LUT[bgr[i] & 0xFF]);
                Lab lab = ColorSpace.linearRgbToLab(linear);
                if (lab.chroma() < ScanThresholds.SUBSTRATE_CHROMA_MAX_NEUTRAL
                        && lab.l() >= ScanThresholds.SUBSTRATE_L_MIN
                        && lab.l() <= ScanThresholds.SUBSTRATE_L_MAX) {
                    neutralSamples.add(linear);
                }
            }
        }
        double neutralRatio = sampledCount == 0 ? 0.0 : (double) neutralSamples.size() / sampledCount;

        CalibrationMethod method;
        double[] gains;
        if (neutralRatio < ScanThresholds.MIN_NEUTRAL_RATIO || neutralSamples.isEmpty()) {
            method = CalibrationMethod.NONE;
            gains = new double[] {1.0, 1.0, 1.0};
        } else {
            method = CalibrationMethod.SUBSTRATE_WB;
            WhiteBalanceSolver.Gains solved = WhiteBalanceSolver.fallback(neutralSamples);
            gains = solved.toArray();
        }

        // --- Buoc 2: ap gain cho TOAN BO pixel ROI -> XYZ (ghi hang loat) + gom substrateLab (S6 buoc 2)
        // va do lech mau trung tinh sau hieu chinh (proxy cho calibration_residual_de00).
        double[] xyzOut = new double[width * height * 3];
        List<Lab> substrateCandidates = new ArrayList<>();
        double residualChromaSum = 0.0;
        int residualCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 3;
                RgbLinear calibrated = new RgbLinear(LINEAR_LUT[bgr[i + 2] & 0xFF] * gains[0],
                        LINEAR_LUT[bgr[i + 1] & 0xFF] * gains[1], LINEAR_LUT[bgr[i] & 0xFF] * gains[2]);
                var xyz = ColorSpace.linearRgbToXyz(calibrated);
                xyzOut[i] = xyz.x();
                xyzOut[i + 1] = xyz.y();
                xyzOut[i + 2] = xyz.z();

                Lab lab = ColorSpace.xyzToLab(xyz);
                if (lab.chroma() < 8.0) {
                    substrateCandidates.add(lab);
                }
                if (x % NEUTRAL_SAMPLE_STRIDE == 0 && y % NEUTRAL_SAMPLE_STRIDE == 0
                        && lab.chroma() < ScanThresholds.SUBSTRATE_CHROMA_MAX_NEUTRAL
                        && lab.l() >= ScanThresholds.SUBSTRATE_L_MIN
                        && lab.l() <= ScanThresholds.SUBSTRATE_L_MAX) {
                    residualChromaSum += lab.chroma();
                    residualCount++;
                }
            }
        }

        Lab substrateLab = substrateCandidates.isEmpty()
                ? new Lab(70.0, 0.0, 0.0)
                : RobustStats.medianPerChannel(substrateCandidates);
        double residualProxy = residualCount == 0 ? 3.0 : residualChromaSum / residualCount;

        Mat xyzMat = new Mat(height, width, opencv_core.CV_64FC3);
        new DoublePointer(xyzMat.data()).put(xyzOut);
        OpenCvXyzFrame xyzFrame = new OpenCvXyzFrame(xyzMat);
        return new CalibrationResult(xyzFrame, method, gains, neutralRatio, residualProxy, substrateLab);
    }
}
