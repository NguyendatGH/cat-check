package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.application.spi.ImageRetentionConsentPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link ImageRetentionConsentPort} đọc thẳng {@code consent_record} (bảng của {@code privacy}),
 * cùng câu SQL với {@code notification.infrastructure.persistence.JdbcConsentGateAdapter}: dòng
 * MỚI NHẤT của cặp (user, purpose) quyết định — bảng append-only, rút consent là một dòng mới.
 */
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
                // Chưa có dòng nào ⇒ chưa đồng ý ("im lặng không phải là đồng ý", p15 §15.3.1 C4).
                .orElse(false);
    }
}
