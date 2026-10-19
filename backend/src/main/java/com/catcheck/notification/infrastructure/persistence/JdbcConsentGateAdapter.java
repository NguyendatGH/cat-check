package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.application.spi.ConsentGatePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcConsentGateAdapter implements ConsentGatePort {

    private final JdbcTemplate jdbc;

    JdbcConsentGateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isGranted(UUID userId, String purposeCode) {
        return jdbc.query("""
                SELECT status
                  FROM consent_record
                 WHERE user_id = ? AND purpose_code = ?
                 ORDER BY occurred_at DESC, created_at DESC
                 LIMIT 1
                """, (rs, rowNum) -> "GRANTED".equals(rs.getString("status")), userId, purposeCode)
                .stream().findFirst()
                .orElse(false);
    }
}
