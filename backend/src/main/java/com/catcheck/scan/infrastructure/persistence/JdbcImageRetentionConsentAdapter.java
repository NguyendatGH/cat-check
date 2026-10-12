package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.application.spi.ImageRetentionConsentPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcImageRetentionConsentAdapter implements ImageRetentionConsentPort {

    private static final String PURPOSE = "SCAN_IMAGE_RETAIN";

    private final JdbcTemplate jdbc;

    JdbcImageRetentionConsentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isGranted(UUID userId) {
        return jdbc.query("""
                SELECT status
                  FROM consent_record
                 WHERE user_id = ? AND purpose_code = ?
                 ORDER BY occurred_at DESC, created_at DESC
                 LIMIT 1
                """, (rs, rowNum) -> "GRANTED".equals(rs.getString("status")), userId, PURPOSE)
                .stream().findFirst()
                .orElse(false);
    }
}
