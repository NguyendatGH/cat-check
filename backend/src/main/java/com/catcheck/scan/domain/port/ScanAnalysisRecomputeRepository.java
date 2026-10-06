package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanAnalysisRecompute;
import com.catcheck.scan.domain.ScanRecomputeImpact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code scan_analysis_recompute} (p4 D4) — nhật ký "tính thử" của backfill
 * bảng màu (L33–L35, p6 §6.5.4).
 *
 * <p>Tách khỏi {@link ScanAnalysisRepository} vì hai bảng có vòng đời khác nhau:
 * {@code scan_analysis} là kết quả người dùng tin cậy, còn bảng này là <b>bản nháp</b> mà
 * L33 ghi đè mỗi lần chạy lại preview.</p>
 */
public interface ScanAnalysisRecomputeRepository {

    void saveAll(List<ScanAnalysisRecompute> rows);

    /**
     * Xoá nhật ký preview cũ của một bảng màu trước khi ghi lượt mới.
     *
     * <p>Không có nó, hai lần bấm "xem trước" sẽ cộng dồn và con số "số bản ghi bị lật phân
     * loại" của L34 đếm gấp đôi. Bảng này là nháp (p4 D4) nên xoá là đúng vòng đời.</p>
     *
     * @return số dòng đã xoá
     */
    int deleteByChartId(UUID chartId);

    /** Tổng hợp tác động cho L34: đã xét bao nhiêu, lật bao nhiêu, {@code deltaPh} lớn nhất. */
    ScanRecomputeImpact impactByChartId(UUID chartId);

    /** Các dòng lệch nhiều nhất, để L34 hiển thị ví dụ cụ thể thay vì chỉ con số tổng. */
    List<ScanAnalysisRecompute> findTopByChartIdOrderByAbsDeltaPhDesc(UUID chartId, int limit);

    /** Mọi dòng preview của một bảng màu — L35 đọc để áp dụng. */
    List<ScanAnalysisRecompute> findByChartId(UUID chartId);

    /** {@code job_run.id} của lượt preview gần nhất (NULL khi chưa chạy preview nào). */
    Optional<UUID> findLatestJobIdByChartId(UUID chartId);
}
