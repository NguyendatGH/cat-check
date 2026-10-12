package com.catcheck.admin.infrastructure.persistence;

import com.catcheck.admin.domain.AdminMetrics;
import com.catcheck.admin.domain.port.AdminMetricsQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminMetricsQueryAdapter implements AdminMetricsQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcAdminMetricsQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminMetrics read() {
        return new AdminMetrics(
                count("SELECT count(*) FROM app_user"),
                count("SELECT count(*) FROM cat WHERE status = 'ACTIVE' AND deleted_at IS NULL"),
                count("SELECT count(*) FROM scan"),
                count("SELECT count(*) FROM job_run WHERE status IN ('FAILED', 'TIMEOUT')"),
                count("SELECT count(*) FROM email_outbox WHERE status = 'PENDING'")
                        + count("SELECT count(*) FROM notification_outbox WHERE status = 'PENDING'"));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
