package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanAnalysis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cổng đọc/ghi {@code scan_analysis} (p4 D3). */
public interface ScanAnalysisRepository {

    ScanAnalysis save(ScanAnalysis analysis);

    Optional<ScanAnalysis> findCurrentByScanId(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);

    Optional<ScanAnalysis> findById(UUID id);

    /**
     * Ứng viên backfill: dòng <b>đang hiện hành</b>, có đủ {@code lab_l/a/b} và nằm trong cửa sổ
     * thời gian admin chọn (p6 §6.5.4).
     *
     * <p>Điều kiện "có Lab" là then chốt: backfill chạy lại <b>chỉ</b> S8→S10 từ Lab đã lưu, nên
     * nó hoạt động kể cả với scan trial (không lưu ảnh) và kể cả sau khi ảnh đã bị xoá theo
     * retention 14 ngày. Dòng không có Lab (ví dụ hàng nhập tay của {@code DevScanController})
     * về nguyên tắc không tính lại được, nên bị loại ở tầng SQL thay vì bị bỏ qua lặng lẽ ở
     * tầng Java.</p>
     *
     * @param computedFrom         mốc {@code computed_at} nhỏ nhất
     * @param engineVersionPrefix  tiền tố {@code engine_version} phải khớp — "cùng major" của
     *                             p6 §6.5.4, truyền dạng {@code "1."}
     * @param limit                cỡ lô (p6 §6.5.4 chốt 500)
     * @param offset               vị trí bắt đầu của lô
     */
    List<ScanAnalysis> findBackfillCandidates(Instant computedFrom, String engineVersionPrefix,
                                              int limit, int offset);

    /** Tổng số ứng viên backfill — để job báo tiến trình trước khi chạy. */
    long countBackfillCandidates(Instant computedFrom, String engineVersionPrefix);
}
