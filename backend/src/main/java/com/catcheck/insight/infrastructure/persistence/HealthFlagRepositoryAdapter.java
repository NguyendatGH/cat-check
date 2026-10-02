package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.HealthFlagSeverity;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link HealthFlagRepository} — CRUD đơn giản qua JPA, {@link #findByFilter} qua
 * {@code JdbcTemplate} vì cần JOIN {@code cat} để lọc theo {@code user_id} (bảng
 * {@code health_flag} chỉ có {@code cat_id}, không có {@code user_id} — p4 D12).
 */
@Repository
class HealthFlagRepositoryAdapter implements HealthFlagRepository {

    private final HealthFlagJpaRepository jpaRepository;
    private final JdbcTemplate jdbc;
    private final JsonMapper objectMapper;
    private final Clock clock;

    HealthFlagRepositoryAdapter(HealthFlagJpaRepository jpaRepository, JdbcTemplate jdbc,
                                 JsonMapper objectMapper, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public Optional<HealthFlag> saveIfNotDuplicate(HealthFlag flag) {
        try {
            return Optional.of(jpaRepository.saveAndFlush(flag));
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(dedupe_key) — cooldown chưa hết hoặc race giữa hai request đồng thời (p4 D12).
            return Optional.empty();
        }
    }

    @Override
    public Optional<HealthFlag> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public HealthFlag save(HealthFlag flag) {
        return jpaRepository.save(flag);
    }

    @Override
    public List<UUID> deleteByTriggerScanId(UUID scanId) {
        List<HealthFlag> flags = jpaRepository.findByTriggerScanId(scanId);
        List<UUID> ids = flags.stream().map(HealthFlag::getId).toList();
        jpaRepository.deleteAll(flags);
        return ids;
    }

    @Override
    public List<HealthFlag> findByCatIdAndTriggeredAtBetween(UUID catId, Instant from, Instant to) {
        return jpaRepository.findByCatIdAndTriggeredAtBetweenOrderByTriggeredAtDesc(catId, from, to);
    }

    @Override
    public boolean isCatOwnedByUser(UUID catId, UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM cat WHERE id = ? AND owner_id = ?", Long.class, catId, userId);
        return count != null && count > 0;
    }

    @Override
    public Page findByFilter(UUID userId, UUID catId, Boolean acknowledged, String severity, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT hf.id, hf.cat_id, hf.rule_code, hf.trigger_scan_id, hf.severity, hf.triggered_at,
                       hf.window_from, hf.window_to, hf.message_key, hf.message_params, hf.explanation_vi,
                       hf.acknowledged_at, hf.acknowledged_by, hf.notification_id, hf.dedupe_key,
                       hf.created_at, hf.updated_at
                  FROM health_flag hf
                  JOIN cat c ON c.id = hf.cat_id
                 WHERE c.owner_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (catId != null) {
            sql.append(" AND hf.cat_id = ?");
            args.add(catId);
        }
        if (acknowledged != null) {
            sql.append(acknowledged ? " AND hf.acknowledged_at IS NOT NULL" : " AND hf.acknowledged_at IS NULL");
        }
        if (severity != null) {
            sql.append(" AND hf.severity = ?");
            args.add(severity);
        }
        CursorKey key = CursorKey.parse(cursor);
        if (key != null) {
            sql.append(" AND (hf.triggered_at, hf.id) < (?, ?)");
            args.add(OffsetDateTime.ofInstant(key.triggeredAt(), java.time.ZoneOffset.UTC));
            args.add(key.id());
        }
        int fetchSize = Math.min(limit + 1, 100);
        sql.append(" ORDER BY hf.triggered_at DESC, hf.id DESC LIMIT ?");
        args.add(fetchSize);

        List<HealthFlag> fetched = jdbc.query(sql.toString(), (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            UUID cat = rs.getObject("cat_id", UUID.class);
            String ruleCode = rs.getString("rule_code");
            UUID triggerScanId = rs.getObject("trigger_scan_id", UUID.class);
            HealthFlagSeverity sev = HealthFlagSeverity.valueOf(rs.getString("severity"));
            Instant windowFrom = rs.getObject("window_from", OffsetDateTime.class).toInstant();
            Instant windowTo = rs.getObject("window_to", OffsetDateTime.class).toInstant();
            String messageKey = rs.getString("message_key");
            Map<String, Object> params = readJson(rs.getString("message_params"));
            String explanationVi = rs.getString("explanation_vi");
            String dedupeKey = rs.getString("dedupe_key");
            OffsetDateTime createdAtRaw = rs.getObject("created_at", OffsetDateTime.class);
            Instant createdAt = createdAtRaw == null ? clock.instant() : createdAtRaw.toInstant();

            HealthFlag flag = new HealthFlag(id, cat, ruleCode, triggerScanId, sev, windowFrom, windowTo,
                    messageKey, params, explanationVi, dedupeKey, createdAt);
            OffsetDateTime acknowledgedAtRaw = rs.getObject("acknowledged_at", OffsetDateTime.class);
            if (acknowledgedAtRaw != null) {
                UUID ackBy = rs.getObject("acknowledged_by", UUID.class);
                flag.acknowledge(ackBy, acknowledgedAtRaw.toInstant());
            }
            return flag;
        }, args.toArray());

        boolean hasMore = fetched.size() > limit;
        List<HealthFlag> items = hasMore ? List.copyOf(fetched.subList(0, limit)) : List.copyOf(fetched);
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            HealthFlag last = items.get(items.size() - 1);
            nextCursor = new CursorKey(last.getTriggeredAt(), last.getId()).encode();
        }
        return new Page(items, nextCursor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private record CursorKey(Instant triggeredAt, UUID id) {

        String encode() {
            String raw = triggeredAt.toEpochMilli() + "." + id;
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
