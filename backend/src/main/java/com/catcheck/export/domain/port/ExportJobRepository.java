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

    record Page(List<ExportJob> items, String nextCursor) {
    }
}
