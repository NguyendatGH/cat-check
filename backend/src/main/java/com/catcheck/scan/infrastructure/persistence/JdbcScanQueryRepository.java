package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.port.ScanQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ScanQueryRepository} trên {@code JdbcTemplate} — mọi câu hỏi hiển thị/thống kê luôn
 * JOIN {@code scan} với {@code scan_analysis} hiện hành (một câu, không N+1). Cùng phong cách
 * với {@code credit.infrastructure.persistence.JdbcCreditLedgerQueryAdapter} (keyset đọc dư 1
 * dòng để biết {@code hasMore}, không COUNT(*) riêng).
 *
 * <p>JOIN thêm {@code color_chart.is_placeholder} bằng SQL thuần (không entity/type Java của
 * module {@code colorchart}) — chỉ để hiển thị {@code chartIsPlaceholder} trong response, không
 * vi phạm R6/R7 (những rule đó nói về kiểu Java/JPA association, không nói về SQL JOIN).</p>
 */
@Repository
class JdbcScanQueryRepository implements ScanQueryRepository {

    private static final int HARD_LIMIT = 100;

    private static final String BASE_SELECT = """
            SELECT s.id AS scan_id, s.cat_id, s.user_id, s.assignment, s.captured_at, s.status,
                   s.is_trial, s.store_image, s.store_image_reason, s.disputed_at, s.disputed_note,
                   s.reassign_count, s.idempotency_key, s.credit_ledger_id,
                   sa.ph_value, sa.ph_low, sa.ph_high, sa.classification, sa.band_id, sa.confidence,
                   sa.near_boundary, sa.lab_l, sa.lab_a, sa.lab_b, sa.match_percent, sa.calibration_method, sa.chart_id,
                   sa.chart_version, sa.engine_version, sa.quality_flags, sa.processing_ms,
                   si.expires_at AS image_expires_at, si.deleted_at AS image_deleted_at,
                   cc.is_placeholder AS chart_is_placeholder
              FROM scan s
              LEFT JOIN scan_analysis sa ON sa.id = s.current_analysis_id
              LEFT JOIN scan_image si ON si.scan_id = s.id
              LEFT JOIN color_chart cc ON cc.id = sa.chart_id
             WHERE s.deleted_at IS NULL
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper objectMapper;

    JdbcScanQueryRepository(JdbcTemplate jdbc, JsonMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page findHistory(HistoryFilter filter, String cursor, int limit) {
        int fetchSize = Math.min(Math.max(limit, 1) + 1, HARD_LIMIT + 1);
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        // INCONCLUSIVE rows are retained for audit/history accounting, but the public history
        // contract exposes only rows that GET /scans/{id} can render.
        sql.append(" AND sa.classification IS NOT NULL AND sa.classification <> 'INCONCLUSIVE'");
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, filter);

        CursorKey key = CursorKey.parse(cursor);
        if (key != null) {
            sql.append(" AND (s.captured_at, s.id) < (?, ?)");
            args.add(RowReaders.utc(key.capturedAt()));
            args.add(key.id());
        }
        sql.append(" ORDER BY s.captured_at DESC, s.id DESC LIMIT ?");
        args.add(fetchSize);

        List<Row> fetched = jdbc.query(sql.toString(), (rs, n) -> map(rs), args.toArray());
        boolean hasMore = fetched.size() > limit;
        List<Row> items = hasMore ? List.copyOf(fetched.subList(0, limit)) : List.copyOf(fetched);

        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Row last = items.get(items.size() - 1);
            nextCursor = new CursorKey(last.capturedAt(), last.scanId()).encode();
        }
        return new Page(items, nextCursor);
    }

    @Override
    public Summary summarize(HistoryFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) AS cnt,
                       count(*) FILTER (WHERE sa.classification = 'INCONCLUSIVE') AS inconclusive_cnt,
                       count(*) FILTER (WHERE sa.confidence < 0.55) AS low_confidence_cnt,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY sa.ph_value) AS median_ph,
                       min(sa.ph_value) AS min_ph,
                       max(sa.ph_value) AS max_ph,
                       min(s.captured_at) AS first_at,
                       max(s.captured_at) AS last_at
                  FROM scan s
                  LEFT JOIN scan_analysis sa ON sa.id = s.current_analysis_id
                 WHERE s.deleted_at IS NULL
                """);
        List<Object> args = new ArrayList<>();
        appendFilter(sql, args, filter);

        Summary base = jdbc.queryForObject(sql.toString(), (rs, n) -> new Summary(
                rs.getLong("cnt"),
                Map.of(),
                RowReaders.decimalOrNull(rs, "median_ph"),
                RowReaders.decimalOrNull(rs, "min_ph"),
                RowReaders.decimalOrNull(rs, "max_ph"),
                rs.getLong("inconclusive_cnt"),
                rs.getLong("low_confidence_cnt"),
                RowReaders.instant(rs, "first_at"),
                RowReaders.instant(rs, "last_at")
        ), args.toArray());

        StringBuilder groupSql = new StringBuilder("""
                SELECT sa.classification AS classification, count(*) AS cnt
                  FROM scan s
                  LEFT JOIN scan_analysis sa ON sa.id = s.current_analysis_id
                 WHERE s.deleted_at IS NULL
                """);
        List<Object> groupArgs = new ArrayList<>();
        appendFilter(groupSql, groupArgs, filter);
        groupSql.append(" GROUP BY sa.classification");

        Map<String, Long> byClassification = new LinkedHashMap<>();
        jdbc.query(groupSql.toString(), rs -> {
            String classification = rs.getString("classification");
            if (classification != null) {
                byClassification.put(classification, rs.getLong("cnt"));
            }
        }, groupArgs.toArray());

        return base == null ? null : new Summary(
                base.count(), byClassification, base.median(), base.min(), base.max(),
                base.inconclusiveCount(), base.lowConfidenceCount(), base.firstAt(), base.lastAt());
    }

    @Override
    public List<RuleRow> findRecentForRules(UUID catId, Instant since, int limit) {
        String sql = """
                SELECT s.id AS scan_id, s.captured_at, sa.classification, sa.ph_value, sa.confidence,
                       sa.near_boundary, sa.calibration_method
                  FROM scan s
                  JOIN scan_analysis sa ON sa.id = s.current_analysis_id
                 WHERE s.deleted_at IS NULL
                   AND s.disputed_at IS NULL
                   AND s.assignment = 'ASSIGNED'
                   AND s.status = 'ANALYZED'
                   AND s.cat_id = ?
                   AND s.captured_at >= ?
                 ORDER BY s.captured_at DESC
                 LIMIT ?
                """;
        return jdbc.query(sql, (rs, n) -> new RuleRow(
                RowReaders.requiredUuid(rs, "scan_id"),
                RowReaders.requiredInstant(rs, "captured_at"),
                RowReaders.textOrNull(rs, "classification"),
                RowReaders.decimalOrNull(rs, "ph_value"),
                RowReaders.decimalOrNull(rs, "confidence"),
                RowReaders.bool(rs, "near_boundary"),
                RowReaders.textOrNull(rs, "calibration_method")
        ), catId, RowReaders.utc(since), limit);
    }

    @Override
    public Optional<Row> findRowByScanId(UUID scanId) {
        String sql = BASE_SELECT + " AND s.id = ?";
        List<Row> rows = jdbc.query(sql, (rs, n) -> map(rs), scanId);
        return rows.stream().findFirst();
    }

    @Override
    public List<Row> findForExport(UUID catId, Instant from, Instant to) {
        // INCONCLUSIVE bị loại — cùng quy ước "không có bản ghi hiển thị được" của GET /scans/{id}
        // (p8 §8.5.4); p13 chỉ đếm "ScanResult hợp lệ", INCONCLUSIVE không phải một kết quả.
        String sql = BASE_SELECT
                + " AND s.cat_id = ? AND s.captured_at >= ? AND s.captured_at <= ?"
                + " AND sa.classification IS NOT NULL AND sa.classification <> 'INCONCLUSIVE'"
                + " ORDER BY s.captured_at DESC";
        return jdbc.query(sql, (rs, n) -> map(rs), catId, RowReaders.utc(from), RowReaders.utc(to));
    }

    private void appendFilter(StringBuilder sql, List<Object> args, HistoryFilter filter) {
        sql.append(" AND s.user_id = ?");
        args.add(filter.userId());
        if (filter.catId() != null) {
            sql.append(" AND s.cat_id = ?");
            args.add(filter.catId());
        }
        if (filter.assignment() != null) {
            sql.append(" AND s.assignment = ?");
            args.add(filter.assignment());
        }
        if (filter.classifications() != null && !filter.classifications().isEmpty()) {
            sql.append(" AND sa.classification IN (")
                    .append(String.join(",", filter.classifications().stream().map(c -> "?").toList()))
                    .append(")");
            args.addAll(filter.classifications());
        }
        if (filter.from() != null) {
            sql.append(" AND s.captured_at >= ?");
            args.add(RowReaders.utc(filter.from()));
        }
        if (filter.to() != null) {
            sql.append(" AND s.captured_at <= ?");
            args.add(RowReaders.utc(filter.to()));
        }
        if (filter.disputed() != null) {
            sql.append(filter.disputed() ? " AND s.disputed_at IS NOT NULL" : " AND s.disputed_at IS NULL");
        }
    }

    private Row map(ResultSet rs) throws SQLException {
        boolean imageStored = rs.getObject("image_expires_at") != null
                && rs.getObject("image_deleted_at") == null;
        return new Row(
                RowReaders.requiredUuid(rs, "scan_id"),
                RowReaders.uuidOrNull(rs, "cat_id"),
                RowReaders.requiredUuid(rs, "user_id"),
                RowReaders.textOrNull(rs, "assignment"),
                RowReaders.requiredInstant(rs, "captured_at"),
                RowReaders.textOrNull(rs, "status"),
                RowReaders.decimalOrNull(rs, "ph_value"),
                RowReaders.decimalOrNull(rs, "ph_low"),
                RowReaders.decimalOrNull(rs, "ph_high"),
                RowReaders.textOrNull(rs, "classification"),
                RowReaders.uuidOrNull(rs, "band_id"),
                RowReaders.decimalOrNull(rs, "confidence"),
                rs.getObject("near_boundary") != null && rs.getBoolean("near_boundary"),
                RowReaders.decimalOrNull(rs, "lab_l"),
                RowReaders.decimalOrNull(rs, "lab_a"),
                RowReaders.decimalOrNull(rs, "lab_b"),
                RowReaders.integerOrNull(rs, "match_percent"),
                RowReaders.textOrNull(rs, "calibration_method"),
                RowReaders.uuidOrNull(rs, "chart_id"),
                RowReaders.integerOrNull(rs, "chart_version"),
                rs.getObject("chart_is_placeholder") != null && rs.getBoolean("chart_is_placeholder"),
                RowReaders.textOrNull(rs, "engine_version"),
                readQualityFlags(rs),
                rs.getObject("credit_ledger_id") != null,
                RowReaders.bool(rs, "is_trial"),
                imageStored,
                RowReaders.instant(rs, "image_expires_at"),
                RowReaders.textOrNull(rs, "store_image_reason"),
                RowReaders.instant(rs, "disputed_at"),
                RowReaders.textOrNull(rs, "disputed_note"),
                rs.getShort("reassign_count"),
                RowReaders.integerOrNull(rs, "processing_ms"),
                RowReaders.textOrNull(rs, "idempotency_key")
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readQualityFlags(ResultSet rs) throws SQLException {
        String json = rs.getString("quality_flags");
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    /** Con trỏ keyset {@code (captured_at, id)} — cùng khuôn với {@code LedgerCursorCodec} của credit. */
    private record CursorKey(Instant capturedAt, UUID id) {

        String encode() {
            String raw = capturedAt.toEpochMilli() + "." + id;
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
