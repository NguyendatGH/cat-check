package com.catcheck.scan.api;

import java.math.BigDecimal;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * Bề mặt backfill bảng màu mà module {@code colorchart} gọi — L33, L34, L35 (p8 §8.4.12 mục (c)).
 *
 * <p><b>Vì sao cổng này nằm ở {@code scan} chứ ở {@code colorchart}:</b> hai bảng bị ghi
 * ({@code scan_analysis}, {@code scan_analysis_recompute}) thuộc module {@code scan} (p4 D3/D4),
 * và toàn bộ phép tính S8→S10 nằm trong {@code scan.domain.color}. Nếu colorchart tự chạy SQL
 * trên hai bảng đó thì đúng loại lệch mà handoff H15.103 đã ghi — ArchUnit không bắt được vì
 * ranh giới Modulith tính theo type Java, không theo tên bảng trong chuỗi SQL.</p>
 *
 * <p>Chiều phụ thuộc: {@code colorchart → scan::api} (thêm vào {@code allowedDependencies} của
 * colorchart), cùng chiều với {@code colorchart → scan::color} đã có. {@code scan} vẫn không
 * biết gì về colorchart — nó đọc bảng màu qua cổng {@code ChartCatalog} do chính nó tuyên bố.</p>
 */
public interface ChartBackfill {

    /** Cỡ lô của job backfill — p6 §6.5.4 chốt 500 dòng. */
    int BATCH_SIZE = 500;

    /**
     * L33 — chạy nền một lượt "tính thử": ghi {@code scan_analysis_recompute}, KHÔNG đổi kết
     * quả nào đang hiển thị.
     *
     * <p>Trả về {@code void}, không trả {@code job_run.id}: dòng {@code job_run} chỉ được mở
     * BÊN TRONG {@code JobRunner} ở luồng nền, nên bất kỳ id trả về ngay cũng là id bịa hoặc
     * {@code null}. Id thật xuất hiện ở {@link #impact} khi job đã chạy — và đó chính là
     * endpoint mà {@code Location} của {@code 202} trỏ tới.</p>
     *
     * @param chartId bảng màu mới (có thể còn {@code DRAFT})
     * @param window  cửa sổ thời gian lùi về quá khứ, ví dụ {@code P90D}
     */
    void startPreview(UUID chartId, Period window);

    /** L34 — tổng hợp tác động của lượt preview gần nhất của một bảng màu. */
    BackfillImpact impact(UUID chartId);

    /**
     * L35 — chạy nền việc áp dụng: tạo dòng {@code scan_analysis} mới ({@code recompute_of} trỏ
     * về bản gốc) và chuyển {@code is_current} sang bản mới (p4 D4 ghi chú nghiệp vụ).
     */
    void startApply(UUID chartId);

    /**
     * Tác động của một lượt preview.
     *
     * @param evaluated     số bản ghi đã tính thử
     * @param flipped       số bản ghi bị <b>lật phân loại</b> — chỉ số cảnh báo chính của p8 L34
     * @param maxAbsDeltaPh {@code |deltaPh|} lớn nhất; {@code null} khi chưa có dòng nào
     * @param jobId         {@code job_run.id} của lượt preview; {@code null} khi chưa chạy
     * @param samples       vài dòng lệch nhiều nhất, để admin thấy ví dụ cụ thể
     */
    record BackfillImpact(
            long evaluated,
            long flipped,
            BigDecimal maxAbsDeltaPh,
            UUID jobId,
            List<BackfillSample> samples) {
    }

    /**
     * Một bản ghi bị ảnh hưởng.
     *
     * @param scanAnalysisId      dòng {@code scan_analysis} gốc
     * @param newPhValue          pH tính theo bảng mới
     * @param newClassification   phân loại theo bảng mới
     * @param deltaPh             chênh lệch so với kết quả hiện hành
     * @param flippedClassification phân loại có bị lật hay không
     */
    record BackfillSample(
            UUID scanAnalysisId,
            BigDecimal newPhValue,
            String newClassification,
            BigDecimal deltaPh,
            boolean flippedClassification) {
    }
}
