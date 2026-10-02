package com.catcheck.reminder.infrastructure.persistence;

import com.catcheck.reminder.application.spi.ReminderFeaturePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link ReminderFeaturePort} đọc cột JSONB {@code user_entitlement.features} (V10).
 *
 * <p>Cùng khuôn {@code cat/infrastructure/persistence/JdbcFeatureEntitlementAdapter}: giá trị có
 * thể là boolean HOẶC chuỗi, nên đọc ra text rồi quy ước bật khi khác
 * {@code false}/{@code null}/{@code 'NONE'}.</p>
 */
@Repository
class JdbcReminderFeatureAdapter implements ReminderFeaturePort {

    private final JdbcTemplate jdbc;

    JdbcReminderFeatureAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isReminderEnabled(UUID userId) {
        List<String> rows = jdbc.query(
                "SELECT features ->> 'reminder' AS value FROM user_entitlement WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("value"),
                userId);
        if (rows.isEmpty()) {
            return false;
        }
        String value = rows.getFirst();
        return value != null && !"false".equalsIgnoreCase(value) && !"NONE".equalsIgnoreCase(value);
    }
}
