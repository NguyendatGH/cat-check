package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.ScanInsightPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ScanInsightPort} doc thang {@code scan} / {@code scan_analysis} / {@code health_flag} /
 * {@code ph_classification_band} bang {@code JdbcTemplate}.
 *
 * <p>KHONG import class nao cua {@code com.catcheck.scan.*} hay {@code com.catcheck.insight.*}
 * — xem javadoc {@link ScanInsightPort} ve ly do kien truc.</p>
 *
 * <p>Bang/cot tra o {@code V11__scan.sql}, {@code V12__monitoring.sql}, {@code V9__colorchart.sql}.
 * Luu y {@code scan_analysis.is_current} — mot scan co the co nhieu ban phan tich (recompute),
 * chi ban {@code is_current = true} moi tinh. Chi lay {@code scan.deleted_at IS NULL} va
 * {@code status = 'ANALYZED'}.</p>
 */
@Repository
class JdbcScanInsightAdapter implements ScanInsightPort {

    private final JdbcTemplate jdbc;

    JdbcScanInsightAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LastScan> lastScanOf(UUID catId) {
        return jdbc.query("""
                SELECT s.id, s.captured_at, sa.ph_value, sa.classification, sa.confidence
                  FROM scan s
                  JOIN scan_analysis sa ON sa.scan_id = s.id AND sa.is_current
                 WHERE s.cat_id = ? AND s.deleted_at IS NULL AND s.status = 'ANALYZED'
                 ORDER BY s.captured_at DESC
                 LIMIT 1
                """,
                (rs, rowNum) -> new LastScan(
                        rs.getObject("id", UUID.class),
                        instant(rs, "captured_at"),
                        rs.getBigDecimal("ph_value"),
                        rs.getString("classification"),
                        rs.getBigDecimal("confidence")),
                catId).stream().findFirst();
    }

    @Override
    public WindowStats windowStats(UUID catId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT count(*)                                                   AS scan_count,
                       count(*) FILTER (WHERE sa.classification = 'IN_RANGE')     AS in_range_count,
                       count(*) FILTER (WHERE sa.classification <> 'INCONCLUSIVE') AS conclusive_count
                  FROM scan s
                  JOIN scan_analysis sa ON sa.scan_id = s.id AND sa.is_current
                 WHERE s.cat_id = ? AND s.deleted_at IS NULL AND s.status = 'ANALYZED'
                   AND s.captured_at >= ? AND s.captured_at <= ?
                """,
                (rs, rowNum) -> new WindowStats(
                        rs.getLong("scan_count"),
                        rs.getLong("in_range_count"),
                        rs.getLong("conclusive_count")),
                catId, from, to)
                .stream().findFirst().orElse(new WindowStats(0, 0, 0));
    }

    @Override
    public long unacknowledgedFlagCount(UUID catId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM health_flag WHERE cat_id = ? AND acknowledged_at IS NULL",
                Long.class, catId);
        return count == null ? 0L : count;
    }

    @Override
    public List<TrendPoint> trendPoints(UUID catId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT s.captured_at, s.disputed_at, sa.ph_value, sa.classification,
                       sa.confidence, sa.near_boundary
                  FROM scan s
                  JOIN scan_analysis sa ON sa.scan_id = s.id AND sa.is_current
                 WHERE s.cat_id = ? AND s.deleted_at IS NULL AND s.status = 'ANALYZED'
                   AND s.captured_at >= ? AND s.captured_at <= ?
                 ORDER BY s.captured_at ASC
                """,
                (rs, rowNum) -> new TrendPoint(
                        instant(rs, "captured_at"),
                        rs.getBigDecimal("ph_value"),
                        rs.getString("classification"),
                        rs.getBigDecimal("confidence"),
                        rs.getBoolean("near_boundary"),
                        rs.getObject("disputed_at", OffsetDateTime.class) != null),
                catId, from, to);
    }

    @Override
    public List<PhBandView> activeBands() {
        return jdbc.query("""
                SELECT code, min_ph, max_ph, severity, label_vi, color_token, sort_order
                  FROM ph_classification_band
                 WHERE active
                 ORDER BY sort_order ASC
                """,
                (rs, rowNum) -> new PhBandView(
                        rs.getString("code"),
                        rs.getBigDecimal("min_ph"),
                        rs.getBigDecimal("max_ph"),
                        rs.getString("severity"),
                        rs.getString("label_vi"),
                        rs.getString("color_token"),
                        rs.getInt("sort_order")));
    }

    @Override
    public List<Integer> chartVersionsIn(UUID catId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT DISTINCT sa.chart_version
                  FROM scan s
                  JOIN scan_analysis sa ON sa.scan_id = s.id AND sa.is_current
                 WHERE s.cat_id = ? AND s.deleted_at IS NULL AND s.status = 'ANALYZED'
                   AND s.captured_at >= ? AND s.captured_at <= ?
                   AND sa.chart_version IS NOT NULL
                 ORDER BY sa.chart_version ASC
                """,
                (rs, rowNum) -> rs.getInt("chart_version"),
                catId, from, to);
    }

    /**
     * Doc cot {@code TIMESTAMPTZ} qua {@code getObject(col, OffsetDateTime.class)} roi chuyen
     * sang {@link Instant} — KHONG dung {@code getTimestamp()}: do la {@code java.sql.Timestamp},
     * bi ArchUnit R13 cam (cung khuon voi {@code privacy.infrastructure.persistence.RowReaders}).
     */
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
