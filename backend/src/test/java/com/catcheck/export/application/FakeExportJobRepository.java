package com.catcheck.export.application;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.port.ExportJobRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code export_job} trong bộ nhớ, giữ nguyên hai ràng buộc thật của p4 G1:
 * {@code UNIQUE(document_code)} và bất biến <b>I25</b>
 * ({@code UNIQUE(user_id) WHERE status IN ('QUEUED','RUNNING')}).
 *
 * <p>I25 là điểm mấu chốt của H15.76: không mô phỏng nó thì test sẽ bỏ qua đúng hậu quả nặng
 * nhất — một dòng kẹt khoá mọi lần export sau của user đó.</p>
 */
final class FakeExportJobRepository implements ExportJobRepository {

    private final LinkedHashMap<UUID, ExportJob> rows = new LinkedHashMap<>();

    /** Mọi trạng thái đã đi qua, theo thứ tự ghi — dùng để khẳng định QUEUED → RUNNING → READY. */
    final List<String> statusTrail = new ArrayList<>();

    @Override
    public ExportJob save(ExportJob job) {
        if (job.isActive()) {
            boolean clash = rows.values().stream()
                    .filter(other -> !other.getId().equals(job.getId()))
                    .filter(other -> other.getUserId().equals(job.getUserId()))
                    .anyMatch(ExportJob::isActive);
            if (clash) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "uq_export_job_active_per_user (p4 G1 I25)");
            }
        }
        rows.put(job.getId(), job);
        statusTrail.add(job.getStatus().name());
        return job;
    }

    @Override
    public Optional<ExportJob> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public Optional<ExportJob> findActiveByUser(UUID userId) {
        return rows.values().stream()
                .filter(job -> job.getUserId().equals(userId))
                .filter(ExportJob::isActive)
                .findFirst();
    }

    @Override
    public Page findByUser(UUID userId, String cursor, int limit) {
        return new Page(rows.values().stream().filter(j -> j.getUserId().equals(userId)).toList(), null);
    }

    @Override
    public boolean existsByDocumentCode(String documentCode) {
        return rows.values().stream().anyMatch(job -> job.getDocumentCode().equals(documentCode));
    }

    @Override
    public List<ExportJob> findStaleActive(Instant cutoff, int limit) {
        return rows.values().stream()
                .filter(ExportJob::isActive)
                .filter(job -> job.getRequestedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(ExportJob::getRequestedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public long countStaleActive(Instant cutoff) {
        return findStaleActive(cutoff, Integer.MAX_VALUE).size();
    }
}
