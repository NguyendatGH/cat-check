package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.DeltaE2000;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.Xyz;
import com.catcheck.scan.domain.color.port.VisionEngine;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point2f;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_objdetect.ArucoDetector;
import org.bytedeco.opencv.opencv_objdetect.Dictionary;
import org.bytedeco.opencv.opencv_objdetect.QRCodeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hiện thực {@link VisionEngine} bằng OpenCV — S3 (phát hiện thẻ) và S6 (tách hạt chỉ thị).
 *
 * <h2>Thứ tự thử S3 (p6 §6.5.1 S3)</h2>
 * <ol>
 *   <li><b>ArUco DICT_4X4_50</b> — độ chính xác hình học tốt nhất cho homography.</li>
 *   <li><b>cv::QRCodeDetector</b> — 3–4 điểm dồn về một góc.</li>
 *   <li><b>contour + approxPolyDP</b> — fallback khi in mờ / ArUco bị che.</li>
 * </ol>
 *
 * <h2>R9: OpenCV chỉ ở đây</h2>
 * <p>Toàn bộ {@code org.bytedeco..} nằm trong {@code scan.infrastructure.vision}. Package
 * {@code scan.domain.color} không import bất kỳ thứ gì từ OpenCV — kiểm thử được mà không cần
 * native (xem {@code OpenCvNativeLoader} cho đường suy giảm khi native lỗi).
 *
 * <h2>Bộ nhớ</h2>
 * <p>Mọi {@link Mat} trong {@code try-with-resources} — quên {@code close()} làm rò native memory,
 * không thấy trong heap dump (p6 §6.5.5).
 */
@Component
public class OpenCvVisionEngine implements VisionEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenCvVisionEngine.class);

    /** Kích thước chuẩn hoá thẻ (p6 S3). */
    private static final int CARD_WIDTH = 600;
    private static final int CARD_HEIGHT = 380;

    private final OpenCvNativeLoader nativeLoader;

    public OpenCvVisionEngine(OpenCvNativeLoader nativeLoader) {
        this.nativeLoader = nativeLoader;
    }

    // ------------------------------------------------------------------ S3

    @Override
    public CardDetectionResult detectCard(FrameHandle image, QuadHint hint) {
        if (!nativeLoader.isAvailable()) {
            log.warn("OpenCV native khong kha dung — bo qua phat hien the");
            return new CardDetectionResult(QuadHint.empty(), null, CardDetectionResult.Method.NONE);
        }
        if (!(image instanceof OpenCvFrameHandle handle)) {
            throw new IllegalArgumentException("Phai dung OpenCvFrameHandle");
        }

        {
            // `handle` sở hữu Mat: KHÔNG đóng ở đây (try-with-resources làm Mat bị giải phóng sau lần dùng đầu,
        // các bước sau của pipeline gặp con trỏ NULL).
            Mat mat = handle.mat();
            // Mỗi tầng dò có thể thấy một ứng viên thoái hoá (tứ giác diện tích 0, điểm trùng nhau…) —
            // warpToCanonical ném IllegalArgumentException. Đó chỉ là "tầng này không tìm được thẻ":
            // rơi xuống tầng sau, cuối cùng NONE (⇒ cờ CARD_NOT_FOUND mức WARN), KHÔNG được làm hỏng
            // cả lần quét.

            // Tier 1: ArUco DICT_4X4_50
            var arucoResult = detectAruco(mat);
            if (arucoResult != null) {
                var warped = tryWarp(mat, arucoResult, CardDetectionResult.Method.ARUCO);
                if (warped != null) {
                    return warped;
                }
            }

            // Tier 2: QRCodeDetector
            var qrResult = detectQr(mat);
            if (qrResult != null) {
                var warped = tryWarp(mat, qrResult, CardDetectionResult.Method.QR);
                if (warped != null) {
                    return warped;
                }
            }

            // Tier 3: contour + approxPolyDP
            var contourResult = detectContour(mat);
            if (contourResult != null) {
                var warped = tryWarp(mat, contourResult, CardDetectionResult.Method.CONTOUR);
                if (warped != null) {
                    return warped;
                }
            }

            return new CardDetectionResult(QuadHint.empty(), null, CardDetectionResult.Method.NONE);
        }
    }

    /**
     * Tier 1 — ArUco DICT_4X4_50.
     *
     * <p>JavaCPP 4.14 không bind hằng {@code DICT_4X4_50} nên tạo Dictionary từ byte list.
     * Nếu tạo không được (thiếu native, sai format) thì trả {@code null} để rơi xuống tier 2 —
     * đúng triết lý suy giảm của p6 §6.5.5.
     */
    private double[][] detectAruco(Mat image) {
        try {
            Dictionary dictionary = createArucoDictionary();
            if (dictionary == null) {
                return null;
            }
            try (ArucoDetector detector = new ArucoDetector(dictionary);
                 MatVector corners = new MatVector();
                 Mat ids = new Mat();
                 MatVector rejected = new MatVector()) {
                detector.detectMarkers(image, corners, ids, rejected);
                if (ids.rows() < 4) {
                    return null;
                }

                // Tìm 4 marker có id 0..3 (góc thẻ)
                List<double[]> points = new ArrayList<>(4);
                for (int id = 0; id < 4; id++) {
                    int found = -1;
                    for (int i = 0; i < ids.rows(); i++) {
                        if (ids.ptr(i).getInt() == id) {
                            found = i;
                            break;
                        }
                    }
                    if (found < 0) {
                        return null;
                    }
                    // Trung tâm marker = trung bình 4 góc
                    Mat cornerMat = corners.get(found);
                    double cx = 0;
                    double cy = 0;
                    for (int j = 0; j < 4; j++) {
                        DoublePointer ptr = new DoublePointer(cornerMat.ptr(j));
                        cx += ptr.get(0);
                        cy += ptr.get(1);
                    }
                    points.add(new double[] {cx / 4.0, cy / 4.0});
                }
                return points.toArray(new double[4][]);
            }
        } catch (Throwable ex) {
            log.debug("ArUco detection that bai, chuyen sang QR: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Tạo Dictionary DICT_4X4_50 từ byte list.
     *
     * <p>JavaCPP 4.14 không bind {@code getPredefinedDictionary} nên phải cung cấp byte list.
     * Byte list được nạp từ resource {@code aruco/dict_4x4_50.bin} — nếu thiếu thì trả
     * {@code null} (rơi xuống tier 2).
     */
    private Dictionary createArucoDictionary() {
        try {
            byte[] bytes = loadArucoDictionaryBytes();
            if (bytes == null) {
                return null;
            }
            // Dictionary(Mat bytesList, int markerSize): mỗi marker 4×4 bit, 4 hàng × 1 byte
            try (Mat byteList = new Mat(bytes.length, 1, opencv_core.CV_8UC1)) {
                byteList.data().put(bytes);
                return new Dictionary(byteList, 4);
            }
        } catch (Throwable ex) {
            log.debug("Khong tao duoc ArUco dictionary: {}", ex.getMessage());
            return null;
        }
    }

    private byte[] loadArucoDictionaryBytes() {
        try (var stream = getClass().getResourceAsStream("/aruco/dict_4x4_50.bin")) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Tier 2 — cv::QRCodeDetector. */
    private double[][] detectQr(Mat image) {
        try (QRCodeDetector qr = new QRCodeDetector();
             Mat points = new Mat();
             Mat straight = new Mat()) {
            qr.detectAndDecode(image, points, straight);
            if (points.rows() < 4) {
                return null;
            }
            double[][] result = new double[4][];
            for (int i = 0; i < 4; i++) {
                DoublePointer ptr = new DoublePointer(points.ptr(i));
                result[i] = new double[] {ptr.get(0), ptr.get(1)};
            }
            return result;
        } catch (Throwable ex) {
            log.debug("QR detection that bai, chuyen sang contour: {}", ex.getMessage());
            return null;
        }
    }

    /** Tier 3 — contour + approxPolyDP (fallback khi in mờ / ArUco bị che). */
    private double[][] detectContour(Mat image) {
        try (Mat gray = new Mat();
             Mat binary = new Mat();
             MatVector contours = new MatVector();
             Mat hierarchy = new Mat()) {
            opencv_imgproc.cvtColor(image, gray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.threshold(gray, binary, 0, 255,
                    opencv_imgproc.THRESH_BINARY_INV + opencv_imgproc.THRESH_OTSU);
            opencv_imgproc.findContours(binary, contours, hierarchy,
                    opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

            double bestArea = 0;
            double[] bestQuad = null;
            for (int i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                double area = opencv_imgproc.contourArea(contour);
                if (area < image.rows() * image.cols() * 0.01) {
                    continue;
                }
                try (Mat approx = new Mat()) {
                    opencv_imgproc.approxPolyDP(contour, approx,
                            0.02 * opencv_imgproc.arcLength(contour, true), true);
                    if (approx.rows() == 4 && area > bestArea) {
                        bestArea = area;
                        bestQuad = new double[8];
                        for (int j = 0; j < 4; j++) {
                            DoublePointer ptr = new DoublePointer(approx.ptr(j));
                            bestQuad[j * 2] = ptr.get(0);
                            bestQuad[j * 2 + 1] = ptr.get(1);
                        }
                    }
                }
            }
            if (bestQuad == null) {
                return null;
            }
            double[][] result = new double[4][];
            for (int i = 0; i < 4; i++) {
                result[i] = new double[] {bestQuad[i * 2], bestQuad[i * 2 + 1]};
            }
            return result;
        } catch (Throwable ex) {
            log.debug("Contour detection that bai: {}", ex.getMessage());
            return null;
        }
    }

    /** {@link #warpToCanonical} nhưng ứng viên thoái hoá ⇒ {@code null} (tầng dò này coi như không thấy thẻ). */
    private CardDetectionResult tryWarp(Mat image, double[][] quad, CardDetectionResult.Method method) {
        try {
            return warpToCanonical(image, quad, method);
        } catch (IllegalArgumentException ex) {
            log.debug("Bo qua ung vien the thoai hoa o tang {}", method);
            return null;
        }
    }

    /** Nắn phẳng thẻ về khung chuẩn 600×380 (p6 S3). */
    private CardDetectionResult warpToCanonical(Mat image, double[][] quad,
                                                 CardDetectionResult.Method method) {
        double[][] ordered = CardQuadOrderingHolder.ordered(quad);

        double[][] dst = {
                {0, 0}, {CARD_WIDTH, 0}, {CARD_WIDTH, CARD_HEIGHT}, {0, CARD_HEIGHT}
        };
        double[][] homography = HomographySolver.solve(ordered, dst);

        try (Mat hMat = new Mat(3, 3, opencv_core.CV_64FC1);
             Mat warped = new Mat()) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    hMat.ptr(r).putDouble(c * 8, homography[r][c]);
                }
            }
            opencv_imgproc.warpPerspective(image, warped, hMat,
                    new Size(CARD_WIDTH, CARD_HEIGHT),
                    opencv_imgproc.INTER_AREA,
                    opencv_core.BORDER_CONSTANT,
                    new org.bytedeco.opencv.opencv_core.Scalar(0, 0, 0, 0));

            QuadHint hint = QuadHint.of(ordered[0], ordered[1], ordered[2], ordered[3]);
            return new CardDetectionResult(hint, new OpenCvFrameHandle(warped.clone()), method);
        }
    }

    // ------------------------------------------------------------------ S6

    /**
     * Tách hạt chỉ thị khỏi nền cát (p6 §6.5.1 S6).
     *
     * <p>Thứ tự gate: loại pixel hỏng → ước lượng nền → candidate → kmeans k=3 → chọn cụm chroma
     * cao nhất → morphology → connected components.
     *
     * <p><b>Lưu ý:</b> chọn cụm theo chroma cao nhất thay vì theo bảng màu vì cổng
     * {@link VisionEngine#selectIndicatorGrains} không nhận bảng màu. Việc dùng bảng màu làm
     * prior (research S6 bước 4) là tối ưu thêm mà A6 có thể thêm ở tầng orchestration.
     */
    @Override
    public List<Blob> selectIndicatorGrains(XyzFrame xyzImage, Lab substrateLab, GrainThresholds thresholds) {
        if (!(xyzImage instanceof OpenCvXyzFrame frame)) {
            throw new IllegalArgumentException("Phai dung OpenCvXyzFrame");
        }

        int width = frame.size().width();
        int height = frame.size().height();
        int totalPixels = width * height;

        // Đọc cả ảnh XYZ một lần (thay cho một lần gọi JNI mỗi pixel).
        double[] xyzAll = frame.toDoubleArray();

        // Gate 1: loại pixel hỏng (specular/shadow) + tính candidate.
        // Nhãn kmeans đánh chỉ số theo danh sách candidate, KHÔNG theo toàn bộ pixel — nên giữ chỉ số
        // pixel (y*width+x) của từng candidate để dựng mask.
        double[] candidates = new double[3 * 1024];
        int[] positions = new int[1024];
        int n = 0;
        for (int p = 0; p < totalPixels; p++) {
            Lab lab = ColorSpace.xyzToLab(new Xyz(xyzAll[3 * p], xyzAll[3 * p + 1], xyzAll[3 * p + 2]));
            if (lab.l() > thresholds.specularLMax() || lab.l() < thresholds.shadowLMin()) {
                continue;
            }
            double chroma = lab.chroma();
            if (chroma < thresholds.chromaMin()) {
                continue;
            }
            double fromSubstrate = DeltaE2000.deltaE(lab, substrateLab, DeltaE2000Params.CAT_CHECK);
            if (fromSubstrate < thresholds.deltaEFromSubstrateMin()) {
                continue;
            }
            if (n == positions.length) {
                positions = java.util.Arrays.copyOf(positions, n * 2);
                candidates = java.util.Arrays.copyOf(candidates, n * 6);
            }
            candidates[3 * n] = 0.3 * lab.l();
            candidates[3 * n + 1] = lab.a();
            candidates[3 * n + 2] = lab.b();
            positions[n] = p;
            n++;
        }

        // Ít candidate hơn một hạt tối thiểu thì không thể có blob nào; kmeans cũng đòi N >= K.
        if (n == 0 || n < Math.max(thresholds.minBlobPx(), 3)) {
            return List.of();
        }

        // kmeans k=3
        int[] labels = kmeans(candidates, n, 3);

        // Chọn cụm có chroma trung bình cao nhất
        int bestCluster = selectBestCluster(candidates, labels, n, 3);

        // Tạo mask + morphology + connected components
        try (Mat mask = new Mat(height, width, opencv_core.CV_8UC1);
             Mat kernel = opencv_imgproc.getStructuringElement(
                     opencv_imgproc.MORPH_RECT, new Size(3, 3))) {
            byte[] maskBytes = new byte[totalPixels];
            for (int i = 0; i < n; i++) {
                if (labels[i] == bestCluster) {
                    maskBytes[positions[i]] = (byte) 255;
                }
            }
            mask.data().put(maskBytes);

            opencv_imgproc.morphologyEx(mask, mask, opencv_imgproc.MORPH_OPEN, kernel);

            try (Mat labelMat = new Mat();
                 Mat stats = new Mat();
                 Mat centroids = new Mat()) {
                int count = opencv_imgproc.connectedComponentsWithStats(
                        mask, labelMat, stats, centroids);

                // Một lượt qua labelMat cho MỌI blob (thay cho một lượt toàn ảnh mỗi blob).
                int[] pixelLabels = new int[totalPixels];
                new IntPointer(labelMat.data()).get(pixelLabels);
                double[] sumL = new double[count];
                double[] sumA = new double[count];
                double[] sumB = new double[count];
                int[] cnt = new int[count];
                for (int p = 0; p < totalPixels; p++) {
                    int label = pixelLabels[p];
                    if (label <= 0) {
                        continue;
                    }
                    Lab lab = ColorSpace.xyzToLab(new Xyz(xyzAll[3 * p], xyzAll[3 * p + 1], xyzAll[3 * p + 2]));
                    sumL[label] += lab.l();
                    sumA[label] += lab.a();
                    sumB[label] += lab.b();
                    cnt[label]++;
                }

                List<Blob> blobs = new ArrayList<>();
                for (int i = 1; i < count; i++) {
                    // `BytePointer.getInt(offset)` đọc theo BYTE: cột CC_STAT_AREA nằm ở 4 * chỉ số cột.
                    int area = stats.ptr(i).getInt(4L * opencv_imgproc.CC_STAT_AREA);
                    if (area < thresholds.minBlobPx()) {
                        continue;
                    }
                    Lab meanLab = cnt[i] > 0
                            ? new Lab(sumL[i] / cnt[i], sumA[i] / cnt[i], sumB[i] / cnt[i])
                            : new Lab(0, 0, 0);
                    blobs.add(new Blob(area, meanLab, area));
                }
                return blobs;
            }
        }
    }

    private int[] kmeans(double[] samples, int n, int k) {
        // `cv::kmeans` đòi dữ liệu CV_32F (assert `type == CV_32F`), không nhận CV_64F.
        float[] flat = new float[3 * n];
        for (int i = 0; i < flat.length; i++) {
            flat[i] = (float) samples[i];
        }
        try (Mat data = new Mat(n, 3, opencv_core.CV_32FC1);
             Mat labels = new Mat();
             Mat centers = new Mat()) {
            new org.bytedeco.javacpp.FloatPointer(data.data()).put(flat);
            opencv_core.kmeans(data, k, labels,
                    new org.bytedeco.opencv.opencv_core.TermCriteria(
                            org.bytedeco.opencv.opencv_core.TermCriteria.EPS
                                    + org.bytedeco.opencv.opencv_core.TermCriteria.MAX_ITER,
                            10, 1.0),
                    3, opencv_core.KMEANS_PP_CENTERS);

            int[] result = new int[n];
            new IntPointer(labels.data()).get(result);
            return result;
        }
    }

    private int selectBestCluster(double[] samples, int[] labels, int n, int k) {
        double[] chromaSum = new double[k];
        int[] count = new int[k];
        for (int i = 0; i < n; i++) {
            int cluster = labels[i];
            chromaSum[cluster] += Math.hypot(samples[3 * i + 1], samples[3 * i + 2]);
            count[cluster]++;
        }
        int best = 0;
        double bestChroma = 0;
        for (int i = 0; i < k; i++) {
            double avg = count[i] > 0 ? chromaSum[i] / count[i] : 0;
            if (avg > bestChroma) {
                bestChroma = avg;
                best = i;
            }
        }
        return best;
    }

    /**
     * Sắp xếp quad về thứ tự TL, TR, BR, BL — delegate sang {@code CardQuadOrdering} của domain
     * (thuần Java, kiểm thử được).
     */
    private static final class CardQuadOrderingHolder {
        static double[][] ordered(double[][] points) {
            var hint = com.catcheck.scan.domain.color.CardQuadOrdering.canonicalize(
                    List.of(points[0], points[1], points[2], points[3]));
            return new double[][] {
                    hint.topLeft(), hint.topRight(), hint.bottomRight(), hint.bottomLeft()
            };
        }
    }
}
