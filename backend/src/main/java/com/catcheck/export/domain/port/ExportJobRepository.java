package com.catcheck.export.domain.port;

import com.catcheck.export.domain.ExportJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository {

    ExportJob save(ExportJob job);

    Optional<ExportJob> findById(UUID id);

    Optional<ExportJob> findActiveByUser(UUID userId);

    Page findByUser(UUID userId, String cursor, int limit);

    boolean existsByDocumentCode(String documentCode);

    List<ExportJob> findStaleActive(java.time.Instant cutoff, int limit);

    long countStaleActive(java.time.Instant cutoff);

    record Page(List<ExportJob> items, String nextCursor) {
    }
}
