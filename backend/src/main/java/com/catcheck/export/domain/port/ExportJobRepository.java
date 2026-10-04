package com.catcheck.export.domain.port;

import com.catcheck.export.domain.ExportJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository {

    ExportJob save(ExportJob job);

    Optional<ExportJob> findById(UUID id);

    /** {@code QUEUED}/{@code RUNNING} hiện có của user, nếu có — ép trần 1 job đang chạy (p13 §13.6.2). */
    Optional<ExportJob> findActiveByUser(UUID userId);

    Page findByUser(UUID userId, String cursor, int limit);

    boolean existsByDocumentCode(String documentCode);

    /**
     * Job còn {@code QUEUED}/{@code RUNNING} mà {@code requested_at} đã cũ hơn {@code cutoff} —
     * nguồn cho {@code ReportPdfTimeoutJob}. Cũ nhất trước, giới hạn {@code limit} dòng
     * (p12 §12.6.1 quy tắc 3a: job luôn xử lý theo lô có {@code LIMIT}).
     */
    List<ExportJob> findStaleActive(java.time.Instant cutoff, int limit);

    /** Đếm job quá hạn — dùng cho chế độ {@code dry_run} (p15 REQ-RET-01). */
    long countStaleActive(java.time.Instant cutoff);

    record Page(List<ExportJob> items, String nextCursor) {
    }
}
