package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link PolicyVersionPort} trên {@code JdbcTemplate}.
 *
 * <p>Không có phương thức sửa/xoá: version đã có consent trỏ tới thì không bao giờ được
 * sửa (p15 REQ-VER-07); V6 đã REVOKE DELETE khỏi {@code catcheck_app}.</p>
 */
@Repository
public class JdbcPolicyVersionAdapter implements PolicyVersionPort {

    private static final String INSERT = """
            INSERT INTO policy_version (
                id, policy_type, version, locale, title, content_md, content_url,
                content_hash, summary_of_changes, requires_reconsent, affected_purposes,
                effective_from, effective_to, published_by, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::varchar[], ?, ?, ?, ?)
            """;

    private static final String COLUMNS = """
            id, policy_type, version, locale, title, content_md, content_url,
            content_hash, summary_of_changes, requires_reconsent, affected_purposes,
            effective_from, effective_to, published_by, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcPolicyVersionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void publish(PolicyVersion version) {
        jdbc.update(INSERT,
                version.id(),
                version.policyType().name(),
                version.version(),
                version.locale(),
                version.title(),
                version.contentMd(),
                version.contentUrl(),
                version.contentHash(),
                version.summaryOfChanges(),
                version.requiresReconsent(),
                version.affectedPurposes().toArray(new String[0]),
                version.effectiveFrom(),
                version.effectiveTo(),
                version.publishedBy(),
                version.createdAt());
    }

    @Override
    public Optional<PolicyVersion> findCurrent(PolicyType type, String locale, Instant now) {
        // Bug that: jdbc.query(...) suy luan kieu SQL tu class runtime cua tham so —
        // java.time.Instant KHONG duoc PostgreSQL JDBC driver ho tro suy luan (chi
        // Timestamp/OffsetDateTime), nem PSQLException "Can't infer the SQL type" (xac
        // nhan that khi test dang ky local). Ep ve java.sql.Timestamp truoc khi truyen.
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM policy_version
                 WHERE policy_type = ?
                   AND locale = ?
                   AND effective_from <= ?
                   AND (effective_to IS NULL OR effective_to > ?)
                 ORDER BY effective_from DESC
                 LIMIT 1
                """, (rs, rowNum) -> readVersion(rs), type.name(), locale,
                now, now).stream().findFirst();
    }

    @Override
    public Optional<PolicyVersion> findById(UUID id) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM policy_version
                 WHERE id = ?
                """, (rs, rowNum) -> readVersion(rs), id).stream().findFirst();
    }

    /**
     * F10 — KHÔNG có điều kiện thời gian trong WHERE: permalink phải trả về được cả bản đã
     * hết hiệu lực (p15 REQ-LEGAL-03). Khoá tự nhiên là UNIQUE (policy_type, version, locale).
     */
    @Override
    public Optional<PolicyVersion> findByTypeAndVersion(PolicyType type, String version, String locale) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM policy_version
                 WHERE policy_type = ? AND version = ? AND locale = ?
                """, (rs, rowNum) -> readVersion(rs), type.name(), version, locale).stream().findFirst();
    }

    @Override
    public List<PolicyVersion> findAllByType(PolicyType type, String locale) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM policy_version
                 WHERE policy_type = ? AND locale = ?
                 ORDER BY effective_from DESC
                """, (rs, rowNum) -> readVersion(rs), type.name(), locale);
    }

    @Override
    public List<PolicyVersion> findAllForAdmin(PolicyType type, String locale, int limit, int offset) {
        // Bộ lọc tuỳ chọn viết bằng "(? IS NULL OR cot = ?)" thay vì ghép chuỗi SQL: cùng một
        // câu lệnh cho mọi tổ hợp bộ lọc nên PostgreSQL tái dùng được plan, và không có đường
        // nào để một tham số lọt vào phần cú pháp.
        String sql = "SELECT " + COLUMNS
                + "  FROM policy_version"
                + " WHERE (?::varchar IS NULL OR policy_type = ?)"
                + "   AND (?::varchar IS NULL OR locale = ?)"
                + " ORDER BY policy_type ASC, effective_from DESC, version DESC"
                + " LIMIT ? OFFSET ?";
        String typeName = type == null ? null : type.name();
        return jdbc.query(sql, (rs, rowNum) -> readVersion(rs),
                typeName, typeName, locale, locale, limit, offset);
    }

    @Override
    public long countForAdmin(PolicyType type, String locale) {
        String typeName = type == null ? null : type.name();
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM policy_version"
                        + " WHERE (?::varchar IS NULL OR policy_type = ?)"
                        + "   AND (?::varchar IS NULL OR locale = ?)",
                Long.class, typeName, typeName, locale, locale);
        return count == null ? 0L : count;
    }

    @Override
    public int markPublished(UUID id, UUID publishedBy, Instant effectiveFrom) {
        // java.time.Instant khong duoc driver PostgreSQL suy luan kieu SQL (xem findCurrent) —
        // ep ve OffsetDateTime truoc khi truyen.
        return jdbc.update(
                "UPDATE policy_version SET published_by = ?, effective_from = ?"
                        + " WHERE id = ? AND published_by IS NULL",
                publishedBy, effectiveFrom.atOffset(java.time.ZoneOffset.UTC), id);
    }

    @Override
    public int closeEffective(PolicyType type, String locale, Instant effectiveTo, UUID exceptId) {
        return jdbc.update(
                "UPDATE policy_version SET effective_to = ?"
                        + " WHERE policy_type = ? AND locale = ? AND id <> ?"
                        + "   AND effective_to IS NULL AND effective_from <= ?",
                effectiveTo.atOffset(java.time.ZoneOffset.UTC), type.name(), locale, exceptId,
                effectiveTo.atOffset(java.time.ZoneOffset.UTC));
    }

    private PolicyVersion readVersion(java.sql.ResultSet rs) {
        try {
            return new PolicyVersion(
                    RowReaders.uuid(rs, "id"),
                    PolicyType.valueOf(rs.getString("policy_type")),
                    RowReaders.requiredString(rs, "version"),
                    RowReaders.requiredString(rs, "locale"),
                    RowReaders.requiredString(rs, "title"),
                    RowReaders.string(rs, "content_md"),
                    RowReaders.string(rs, "content_url"),
                    RowReaders.requiredString(rs, "content_hash"),
                    RowReaders.string(rs, "summary_of_changes"),
                    rs.getBoolean("requires_reconsent"),
                    RowReaders.stringList(rs, "affected_purposes"),
                    RowReaders.requiredInstant(rs, "effective_from"),
                    RowReaders.instant(rs, "effective_to"),
                    RowReaders.uuid(rs, "published_by"),
                    RowReaders.requiredInstant(rs, "created_at"));
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Đọc policy_version thất bại", ex);
        }
    }
}
