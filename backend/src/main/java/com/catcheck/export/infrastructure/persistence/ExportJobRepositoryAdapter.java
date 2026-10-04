package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.ExportStatus;
import com.catcheck.export.domain.port.ExportJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ExportJobRepository} — CRUD qua JPA; {@link #findByUser} dùng {@code JdbcTemplate} với
 * keyset {@code (requested_at, id)} (cùng khuôn với {@code scan}/{@code credit}) vì số lượng bản
 * ghi mỗi user nhỏ nhưng vẫn giữ đúng hợp đồng phân trang con trỏ của p8.
 */
@Repository
class ExportJobRepositoryAdapter implements ExportJobRepository {

    private final ExportJobJpaRepository jpaRepository;
    private final JdbcTemplate jdbc;

    ExportJobRepositoryAdapter(ExportJobJpaRepository jpaRepository, JdbcTemplate jdbc) {
        this.jpaRepository = jpaRepository;
        this.jdbc = jdbc;
    }

    @Override
    public ExportJob save(ExportJob job) {
        return jpaRepository.save(job);
    }

    @Override
    public Optional<ExportJob> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ExportJob> findActiveByUser(UUID userId) {
        return jpaRepository.findFirstByUserIdAndStatusIn(
                userId, List.of(ExportStatus.QUEUED, ExportStatus.RUNNING));
    }

    @Override
    public boolean existsByDocumentCode(String documentCode) {
        return jpaRepository.existsByDocumentCode(documentCode);
    }

    private static final List<ExportStatus> ACTIVE_STATUSES =
            List.of(ExportStatus.QUEUED, ExportStatus.RUNNING);

    @Override
    public List<ExportJob> findStaleActive(Instant cutoff, int limit) {
        return jpaRepository.findByStatusInAndRequestedAtLessThanOrderByRequestedAtAsc(
                ACTIVE_STATUSES, cutoff, org.springframework.data.domain.Limit.of(limit));
    }

    @Override
    public long countStaleActive(Instant cutoff) {
        return jpaRepository.countByStatusInAndRequestedAtLessThan(ACTIVE_STATUSES, cutoff);
    }

    @Override
    public Page findByUser(UUID userId, String cursor, int limit) {
        int fetchSize = Math.min(limit + 1, 100);
        StringBuilder sql = new StringBuilder("SELECT id FROM export_job WHERE user_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        CursorKey key = CursorKey.parse(cursor);
        if (key != null) {
            sql.append(" AND (requested_at, id) < (?, ?)");
            args.add(OffsetDateTime.ofInstant(key.requestedAt(), java.time.ZoneOffset.UTC));
            args.add(key.id());
        }
        sql.append(" ORDER BY requested_at DESC, id DESC LIMIT ?");
        args.add(fetchSize);

        List<UUID> ids = jdbc.query(sql.toString(),
                (rs, n) -> rs.getObject("id", UUID.class), args.toArray());
        List<ExportJob> fetched = ids.stream()
                .map(id -> jpaRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        boolean hasMore = fetched.size() > limit;
        List<ExportJob> items = hasMore ? List.copyOf(fetched.subList(0, limit)) : fetched;
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            ExportJob last = items.get(items.size() - 1);
            nextCursor = new CursorKey(last.getRequestedAt(), last.getId()).encode();
        }
        return new Page(items, nextCursor);
    }

    private record CursorKey(Instant requestedAt, UUID id) {

        String encode() {
            String raw = requestedAt.toEpochMilli() + "." + id;
            return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        static CursorKey parse(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                String raw = new String(java.util.Base64.getUrlDecoder().decode(cursor),
                        java.nio.charset.StandardCharsets.UTF_8);
                int sep = raw.indexOf('.');
                if (sep <= 0) {
                    return null;
                }
                return new CursorKey(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, sep))),
                        UUID.fromString(raw.substring(sep + 1)));
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }
}
