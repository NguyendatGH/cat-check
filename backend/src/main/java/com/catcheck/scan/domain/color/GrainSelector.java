package com.catcheck.scan.domain.color;

import java.util.List;

/**
 * Tách hạt chỉ thị khỏi nền cát — quyết định thuần Java cho bước S6 (p6 §6.5.1 S6).
 *
 * <p>Việc tìm vùng liên thông để lấy là việc của OpenCV; <b>quyết định</b> một vùng có phải hạt
 * chỉ thị hay không là quyết định nghiệp vụ, nên nằm ở đây để kiểm thử được mà không cần thư viện
 * native. Đầu vào là một danh sách vùng (blob) đã tìm thấy cùng màu Lab trung bình mỗi vùng.
 *
 * <h2>Ba điều kiện cùng lúc</h2>
 * <ol>
 *   <li><b>Không phải pixel bắt sáng hay quá tối</b> — hạt nằm trong bóng sâu hoặc điểm lấp loáng
 *       phản ánh hình học và phơi sáng, không phải màu chỉ thị.</li>
 *   <li><b>Chroma đủ lớn</b> — cát trung tính có chroma thấp; hạt chỉ thị có màu.</li>
 *   <li><b>Xa màu nền đủ</b> — ΔE00 tới màu nền cát ≥ {@code deltaEFromSubstrateMin}. Điều kiện này
 *       mới là điều kiện thật sự phân biệt: một cụm cát ẩm sẫm màu có chroma cao nhưng vẫn không
 *       phải hạt.</li>
 * </ol>
 */
public final class GrainSelector {

    private GrainSelector() {
        throw new AssertionError("GrainSelector la lop tien ich, khong instantiate");
    }

    /**
     * Một vùng liên thông đã tìm thấy, do tầng hạ tầng cung cấp.
     *
     * @param areaPx   diện tích pixel
     * @param meanLab  màu trung bình của vùng
     * @param maxL     L* lớn nhất trong vùng — dùng để loại vùng lẫn tia sáng lóa
     * @param minL     L* nhỏ nhất trong vùng
     */
    public record Candidate(int areaPx, Lab meanLab, double maxL, double minL) {
    }

    /**
     * Kết quả lọc.
     *
     * @param kept             các hạt giữ lại
     * @param indicatorRatio   tỉ lệ pixel hạt chỉ thị / tổng pixel vùng quan tâm
     * @param offCurve         hạt không nằm trên dải màu ACTIVE (cluster gần nhất > {@code clusterMaxΔE00})
     */
    public record Selection(List<Lab> kept, double indicatorRatio, boolean offCurve) {

        public boolean isEmpty() {
            return kept.isEmpty();
        }
    }

    /**
     * Lọc các vùng thành hạt chỉ thị.
     *
     * @param candidates  vùng đã tìm thấy
     * @param substrateLab màu nền cát ước lượng (median ROI, p6 S6)
     * @param thresholds  ngưỡng của dòng sản phẩm
     * @param roiPixelCount số pixel của vùng quan tâm, dùng tính tỉ lệ phủ
     * @param nearestChartDeltaE ΔE00 tới ô bảng màu ACTIVE gần nhất, dùng cho cờ {@code OFF_CURVE}
     */
    public static Selection select(List<Candidate> candidates, Lab substrateLab,
                                   VisionPortThresholds thresholds, int roiPixelCount,
                                   double nearestChartDeltaE) {
        if (candidates == null) {
            throw new IllegalArgumentException("Danh sach vung khong duoc null");
        }
        if (roiPixelCount <= 0) {
            throw new IllegalArgumentException("Vung quan tam phai co it nhat 1 pixel");
        }

        List<Lab> kept = new java.util.ArrayList<>(candidates.size());
        int keptPixels = 0;

        for (Candidate candidate : candidates) {
            if (candidate.areaPx() < thresholds.minBlobPx()) {
                continue;
            }
            if (candidate.maxL() > thresholds.specularLMax() || candidate.minL() < thresholds.shadowLMin()) {
                continue;
            }
            double chroma = Math.hypot(candidate.meanLab().a(), candidate.meanLab().b());
            if (chroma < thresholds.chromaMin()) {
                continue;
            }
            double fromSubstrate = DeltaE2000.deltaE(candidate.meanLab(), substrateLab,
                    DeltaE2000Params.CAT_CHECK);
            if (fromSubstrate < thresholds.deltaEFromSubstrateMin()) {
                continue;
            }
            kept.add(candidate.meanLab());
            keptPixels += candidate.areaPx();
        }

        double ratio = Math.min(1.0, (double) keptPixels / roiPixelCount);
        boolean offCurve = nearestChartDeltaE > thresholds.clusterMaxDeltaE();

        return new Selection(List.copyOf(kept), ratio, offCurve);
    }

    /**
     * Ngưỡng S6 cho dòng sản phẩm. Tách riêng khỏi {@code VisionEngine.GrainThresholds} để lớp này
     * không phụ thuộc cổng thị giác — cùng ngữ nghĩa nhưng khác phạm vi phụ thuộc.
     *
     * @param minCoverage        ngưỡng phủ mềm (STANDARD 0.015) → cờ {@code LOW_GRANULE_COVERAGE}
     * @param hardMinCoverage    ngưỡng phủ cứng (0.005) → {@code INCONCLUSIVE}
     * @param minBlobPx          diện tích tối thiểu
     * @param specularLMax       trần L* loại pixel bắt sáng
     * @param shadowLMin         sàn L* loại pixel quá tối
     * @param chromaMin          chroma tối thiểu
     * @param deltaEFromSubstrateMin ΔE00 tối thiểu so với nền
     * @param clusterMaxDeltaE   ΔE00 tối đa để coi là nằm trên dải màu
     */
    public record VisionPortThresholds(double minCoverage, double hardMinCoverage, int minBlobPx,
                                      double specularLMax, double shadowLMin, double chromaMin,
                                      double deltaEFromSubstrateMin, double clusterMaxDeltaE) {

        /** Bộ ngưỡng chuẩn của dòng STANDARD (p6 S6). */
        public static VisionPortThresholds standard() {
            return new VisionPortThresholds(0.015, 0.005, 12, 95.0, 15.0, 10.0, 8.0, 14.0);
        }
    }

    /**
     * Ngưỡng phủ bị vượt → {@code INCONCLUSIVE} (p6 S6, cột "điều kiện thất bại").
     *
     * <p>Phân biệt hai ngưỡng là cố ý: dưới {@code minCoverage} thì đủ để trả kết quả kèm cờ cảnh báo,
     * dưới {@code hardMinCoverage} thì dữ liệu quá mỏng để kết luận và phải nói "chưa đủ dữ liệu"
     * thay vì đoán.
     */
    public static boolean isInconclusive(Selection selection, VisionPortThresholds thresholds) {
        return selection.indicatorRatio() < thresholds.hardMinCoverage() || selection.isEmpty();
    }

    /** Dưới ngưỡng mềm → gắn cờ {@code LOW_GRANULE_COVERAGE} nhưng vẫn trả kết quả. */
    public static boolean isLowCoverage(Selection selection, VisionPortThresholds thresholds) {
        return selection.indicatorRatio() < thresholds.minCoverage();
    }
}
