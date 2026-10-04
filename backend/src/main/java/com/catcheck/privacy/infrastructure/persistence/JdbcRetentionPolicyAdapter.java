package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.RetentionAction;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.privacy.domain.port.RetentionDryRunPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link RetentionPolicyPort} trên {@code JdbcTemplate} — bảng cấu hình, đọc/ghi qua
 * API admin (M6). Bất biến I15 (SCAN_IMAGE ≤ 14 ngày) ép ở tầng service, không ép ở đây.
 */
@Repository
public class JdbcRetentionPolicyAdapter implements RetentionPolicyPort, RetentionDryRunPort {

    private static final String COLUMNS = """
            code, data_inventory_code, target_table, retention_days, anchor_column,
            action_on_expiry, job_name, safety_threshold_percent, enabled, legal_basis,
            updated_by, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcRetentionPolicyAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RetentionPolicy> findAll() {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM retention_policy
                 ORDER BY code
                """, (rs, rowNum) -> readPolicy(rs));
    }

    @Override
    public Optional<RetentionPolicy> findByCode(String code) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM retention_policy
                 WHERE code = ?
                """, (rs, rowNum) -> readPolicy(rs), code).stream().findFirst();
    }

    @Override
    public void save(RetentionPolicy policy) {
        jdbc.update("""
                INSERT INTO retention_policy (
                    code, data_inventory_code, target_table, retention_days, anchor_column,
                    action_on_expiry, job_name, safety_threshold_percent, enabled, legal_basis,
                    updated_by, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    data_inventory_code = EXCLUDED.data_inventory_code,
                    target_table = EXCLUDED.target_table,
                    retention_days = EXCLUDED.retention_days,
                    anchor_column = EXCLUDED.anchor_column,
                    action_on_expiry = EXCLUDED.action_on_expiry,
                    job_name = EXCLUDED.job_name,
                    safety_threshold_percent = EXCLUDED.safety_threshold_percent,
                    enabled = EXCLUDED.enabled,
                    legal_basis = EXCLUDED.legal_basis,
                    updated_by = EXCLUDED.updated_by
                """,
                policy.code(),
                policy.dataInventoryCode(),
                policy.targetTable(),
                policy.retentionDays(),
                policy.anchorColumn(),
                policy.actionOnExpiry().name(),
                policy.jobName(),
                policy.safetyThresholdPercent(),
                policy.enabled(),
                policy.legalBasis(),
                policy.updatedBy(),
                policy.createdAt());
    }

    @Override
    public long countExpired(RetentionPolicy policy, java.time.Instant cutoff) {
        if (!policy.targetTable().matches("[a-z][a-z0-9_]*")
                || !policy.anchorColumn().matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Retention dry-run chỉ cho phép identifier SQL đơn giản");
        }
        String sql = "SELECT count(*) FROM \"" + policy.targetTable() + "\""
                + " WHERE \"" + policy.anchorColumn() + "\" < ?";
        Long count = jdbc.queryForObject(sql, Long.class, cutoff);
        return count == null ? 0 : count;
    }

    private RetentionPolicy readPolicy(java.sql.ResultSet rs) {
        try {
            return new RetentionPolicy(
                    RowReaders.requiredString(rs, "code"),
                    RowReaders.string(rs, "data_inventory_code"),
                    RowReaders.requiredString(rs, "target_table"),
                    RowReaders.boxedInt(rs, "retention_days"),
                    RowReaders.requiredString(rs, "anchor_column"),
                    RetentionAction.valueOf(rs.getString("action_on_expiry")),
                    RowReaders.string(rs, "job_name"),
                    rs.getInt("safety_threshold_percent"),
                    rs.getBoolean("enabled"),
                    RowReaders.string(rs, "legal_basis"),
                    RowReaders.uuid(rs, "updated_by"),
                    RowReaders.requiredInstant(rs, "created_at"));
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Đọc retention_policy thất bại", ex);
        }
    }
}
