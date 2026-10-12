package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.DeltaE2000;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.LinearizationCurve;
import com.catcheck.scan.domain.color.Matrix3;
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
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Component
public class OpenCvRawImageProcessor implements RawImagePort {

    private static final Logger log = LoggerFactory.getLogger(OpenCvRawImageProcessor.class);

    private static final int NEUTRAL_SAMPLE_STRIDE = 3;

    static final int ANALYSIS_MAX_EDGE = 1280;

    static final double PATCH_BORDER_TRIM = 0.12;

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
        final Mat source = handle.mat();
        try (Mat gray = new Mat();
             Mat laplacian = new Mat();
             Mat mean = new Mat();
             Mat stddev = new Mat()) {

            opencv_imgproc.cvtColor(source, gray, opencv_imgproc.COLOR_BGR2GRAY);

            opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);
            opencv_core.meanStdDev(laplacian, mean, stddev);
            double blurVariance = Math.pow(new DoublePointer(stddev.ptr()).get(0), 2);

            double meanLuma = opencv_core.mean(gray).get(0);

            int total = gray.rows() * gray.cols();
            int clipHighCount = countAboveOrBelow(gray, 250, true);
            int clipLowCount = countAboveOrBelow(gray, 5, false);
            double clipHigh = total == 0 ? 0.0 : (double) clipHighCount / total;
            double clipLow = total == 0 ? 0.0 : (double) clipLowCount / total;

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
    public CalibrationResult calibrate(VisionEngine.FrameHandle roiFrame, Lab referenceSubstrateLab) {
        return withAnalysisScale(asMat(roiFrame), work -> calibrateOn(work, referenceSubstrateLab));
    }

    @Override
    public CalibrationResult applyCardCcm(VisionEngine.FrameHandle roiFrame, LinearizationCurve curve, Matrix3 ccm,
                                           double residualDe00) {
        return withAnalysisScale(asMat(roiFrame), work -> applyCardCcmOn(work, curve, ccm, residualDe00));
    }

    private Mat asMat(VisionEngine.FrameHandle frame) {
        if (!(frame instanceof OpenCvFrameHandle handle)) {
            throw new IllegalArgumentException("Phai dung OpenCvFrameHandle");
        }
        return handle.mat();
    }

    private CalibrationResult withAnalysisScale(Mat source, Function<Mat, CalibrationResult> onWorkingFrame) {
        Mat scaled = null;
        try {
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
            return onWorkingFrame.apply(work);
        } finally {
            if (scaled != null) {
                scaled.close();
            }
        }
    }

    private static final double[] LINEAR_LUT = buildLut();

    private static double[] buildLut() {
        double[] lut = new double[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = ColorSpace.srgb8ToLinear(i);
        }
        return lut;
    }

    private CalibrationResult calibrateOn(Mat source, Lab referenceSubstrateLab) {
        int width = source.cols();
        int height = source.rows();
        Mat contiguous = source.isContinuous() ? source : source.clone();
        byte[] bgr = new byte[width * height * 3];
        try {
            contiguous.data().get(bgr);
        } finally {
            if (contiguous != source) {
                contiguous.close();
            }
        }

        List<RgbLinear> substrateSamples = new ArrayList<>();
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
                    substrateSamples.add(linear);
                }
            }
        }
        double neutralRatio = sampledCount == 0 ? 0.0 : (double) substrateSamples.size() / sampledCount;

        CalibrationMethod method;
        double[] gains;
        if (neutralRatio < ScanThresholds.MIN_NEUTRAL_RATIO || substrateSamples.isEmpty()) {
            method = CalibrationMethod.NONE;
            gains = new double[] {1.0, 1.0, 1.0};
        } else {
            method = CalibrationMethod.SUBSTRATE_WB;
            WhiteBalanceSolver.Gains solved = referenceSubstrateLab == null
                    ? WhiteBalanceSolver.fallback(substrateSamples)
                    : WhiteBalanceSolver.fallbackToReference(
                            substrateSamples, ColorSpace.labToLinearRgb(referenceSubstrateLab));
            gains = solved.toArray();
        }

        double[] xyzOut = new double[width * height * 3];
        List<Lab> substrateCandidates = new ArrayList<>();
        List<Double> residuals = new ArrayList<>();
        RgbLinear referenceRgb = referenceSubstrateLab == null
                ? RgbLinear.WHITE
                : ColorSpace.labToLinearRgb(referenceSubstrateLab);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 3;
                RgbLinear raw = new RgbLinear(LINEAR_LUT[bgr[i + 2] & 0xFF],
                        LINEAR_LUT[bgr[i + 1] & 0xFF], LINEAR_LUT[bgr[i] & 0xFF]);
                Lab rawLab = ColorSpace.linearRgbToLab(raw);
                boolean isSubstrate = isSubstrateCandidate(rawLab);
                RgbLinear calibrated = raw.times(gains[0], gains[1], gains[2]);
                var xyz = ColorSpace.linearRgbToXyz(calibrated);
                xyzOut[i] = xyz.x();
                xyzOut[i + 1] = xyz.y();
                xyzOut[i + 2] = xyz.z();

                Lab lab = ColorSpace.xyzToLab(xyz);
                if (isSubstrate) {
                    substrateCandidates.add(lab);
                }
                if (x % NEUTRAL_SAMPLE_STRIDE == 0 && y % NEUTRAL_SAMPLE_STRIDE == 0
                        && isSubstrate) {
                    double scale = calibrated.mean() / Math.max(referenceRgb.mean(), 1e-6);
                    Lab expectedAtSameLightness = ColorSpace.linearRgbToLab(referenceRgb.times(scale));
                    residuals.add(DeltaE2000.deltaE(lab, expectedAtSameLightness));
                }
            }
        }

        Lab substrateLab = substrateCandidates.isEmpty()
                ? new Lab(70.0, 0.0, 0.0)
                : RobustStats.medianPerChannel(substrateCandidates);
        double residualProxy = residuals.isEmpty() ? 3.0 : medianDouble(residuals);

        Lab observedSubstrateLab = substrateSamples.isEmpty()
                ? null
                : RobustStats.medianPerChannel(
                        substrateSamples.stream().map(ColorSpace::linearRgbToLab).toList());
        log.info("scan.calibration substrateDetected={} method={} candidates={}/{} ratio={} "
                        + "observedLab={} referenceLab={} correctedLab={} gains=[{}, {}, {}] residualDe00={}",
                !substrateSamples.isEmpty(), method, substrateSamples.size(), sampledCount, neutralRatio,
                observedSubstrateLab, referenceSubstrateLab, substrateLab,
                gains[0], gains[1], gains[2], residualProxy);

        Mat xyzMat = new Mat(height, width, opencv_core.CV_64FC3);
        new DoublePointer(xyzMat.data()).put(xyzOut);
        OpenCvXyzFrame xyzFrame = new OpenCvXyzFrame(xyzMat);
        return new CalibrationResult(xyzFrame, method, gains, neutralRatio, residualProxy, substrateLab);
    }

    private boolean isSubstrateCandidate(Lab lab) {
        return lab.chroma() < ScanThresholds.SUBSTRATE_CHROMA_MAX_NEUTRAL
                && lab.l() >= ScanThresholds.SUBSTRATE_L_MIN
                && lab.l() <= ScanThresholds.SUBSTRATE_L_MAX;
    }

    private double medianDouble(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0
                : sorted.get(middle);
    }


    @Override
    public List<CardPatchSample> sampleNeutralPatches(VisionEngine.FrameHandle canonicalCard, CardLayout cardLayout) {
        Mat mat = asMat(canonicalCard);
        int width = mat.cols();
        int height = mat.rows();
        Mat contiguous = mat.isContinuous() ? mat : mat.clone();
        byte[] bgr = new byte[width * height * 3];
        try {
            contiguous.data().get(bgr);
        } finally {
            if (contiguous != mat) {
                contiguous.close();
            }
        }

        List<CardPatchSample> samples = new ArrayList<>();
        for (CardLayout.Patch patch : cardLayout.neutralPatches()) {
            int[] medianBgr = medianCoreChannels(bgr, width, height, patch);
            if (medianBgr == null) {
                continue;
            }
            samples.add(new CardPatchSample(patch.id(), medianBgr[2], medianBgr[1], medianBgr[0], patch.refLab()));
        }
        return samples;
    }

    private int[] medianCoreChannels(byte[] bgr, int width, int height, CardLayout.Patch patch) {
        int x0 = (int) Math.round(patch.x() * width);
        int y0 = (int) Math.round(patch.y() * height);
        int pw = (int) Math.round(patch.w() * width);
        int ph = (int) Math.round(patch.h() * height);
        int trimX = (int) Math.round(pw * PATCH_BORDER_TRIM);
        int trimY = (int) Math.round(ph * PATCH_BORDER_TRIM);
        int xs = clamp(x0 + trimX, 0, width - 1);
        int ys = clamp(y0 + trimY, 0, height - 1);
        int xe = clamp(x0 + pw - trimX, xs + 1, width);
        int ye = clamp(y0 + ph - trimY, ys + 1, height);

        List<Integer> bs = new ArrayList<>();
        List<Integer> gs = new ArrayList<>();
        List<Integer> rs = new ArrayList<>();
        for (int y = ys; y < ye; y++) {
            for (int x = xs; x < xe; x++) {
                int i = (y * width + x) * 3;
                bs.add(bgr[i] & 0xFF);
                gs.add(bgr[i + 1] & 0xFF);
                rs.add(bgr[i + 2] & 0xFF);
            }
        }
        if (bs.isEmpty()) {
            return null;
        }
        return new int[] {median(bs), median(gs), median(rs)};
    }

    private int median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private double[] buildLinearizationLut(LinearizationCurve curve) {
        double[] lut = new double[256];
        for (int i = 0; i < 256; i++) {
            lut[i] = curve.linearize(i / 255.0);
        }
        return lut;
    }

    private CalibrationResult applyCardCcmOn(Mat source, LinearizationCurve curve, Matrix3 ccm, double residualDe00) {
        int width = source.cols();
        int height = source.rows();
        Mat contiguous = source.isContinuous() ? source : source.clone();
        byte[] bgr = new byte[width * height * 3];
        try {
            contiguous.data().get(bgr);
        } finally {
            if (contiguous != source) {
                contiguous.close();
            }
        }

        double[] lut = buildLinearizationLut(curve);
        double[] ccmArray = ccm.toArray();
        double[] identityGains = {1.0, 1.0, 1.0};

        double[] xyzOut = new double[width * height * 3];
        List<Lab> substrateCandidates = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 3;
                RgbLinear raw = new RgbLinear(lut[bgr[i + 2] & 0xFF], lut[bgr[i + 1] & 0xFF], lut[bgr[i] & 0xFF]);
                RgbLinear corrected = VisionEngine.corrected(raw, identityGains, ccmArray);
                var xyz = ColorSpace.linearRgbToXyz(corrected);
                xyzOut[i] = xyz.x();
                xyzOut[i + 1] = xyz.y();
                xyzOut[i + 2] = xyz.z();

                Lab lab = ColorSpace.xyzToLab(xyz);
                if (lab.chroma() < 8.0) {
                    substrateCandidates.add(lab);
                }
            }
        }

        Lab substrateLab = substrateCandidates.isEmpty()
                ? new Lab(70.0, 0.0, 0.0)
                : RobustStats.medianPerChannel(substrateCandidates);

        Mat xyzMat = new Mat(height, width, opencv_core.CV_64FC3);
        new DoublePointer(xyzMat.data()).put(xyzOut);
        OpenCvXyzFrame xyzFrame = new OpenCvXyzFrame(xyzMat);
        return new CalibrationResult(xyzFrame, CalibrationMethod.CARD_CCM, identityGains, 1.0, residualDe00,
                substrateLab);
    }
}
