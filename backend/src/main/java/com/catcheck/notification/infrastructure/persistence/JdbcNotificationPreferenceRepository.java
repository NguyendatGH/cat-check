package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.domain.AttentionAlertChannel;
import com.catcheck.notification.domain.QuietHours;
import com.catcheck.notification.domain.UserNotificationPreference;
import com.catcheck.notification.domain.port.NotificationPreferenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.UUID;

/**
 * ĐỌC {@code user_notification_preference} (V13, p4 F4).
 *
 * <p>Chỉ đọc: đường GHI thuộc {@code identity} (p8 B11). Hai module cùng ghi một bảng là hai
 * nguồn sự thật.</p>
 */
@Repository
class JdbcNotificationPreferenceRepository implements NotificationPreferenceRepository {

    private final JdbcTemplate jdbc;

    JdbcNotificationPreferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserNotificationPreference findByUserIdOrDefaults(UUID userId) {
        return jdbc.query("""
                SELECT attention_alert_channel, credit_alerts_enabled, report_ready_enabled,
                       image_retention_warning_enabled, normal_result_enabled,
                       quiet_hours_start, quiet_hours_end, quiet_hours_enabled
                  FROM user_notification_preference
                 WHERE user_id = ?
                """, (rs, rowNum) -> new UserNotificationPreference(
                        AttentionAlertChannel.valueOf(rs.getString("attention_alert_channel")),
                        rs.getBoolean("credit_alerts_enabled"),
                        rs.getBoolean("report_ready_enabled"),
                        rs.getBoolean("image_retention_warning_enabled"),
                        rs.getBoolean("normal_result_enabled"),
                        new QuietHours(
                                rs.getBoolean("quiet_hours_enabled"),
                                rs.getObject("quiet_hours_start", LocalTime.class),
                                rs.getObject("quiet_hours_end", LocalTime.class))),
                userId)
                .stream().findFirst()
                .orElseGet(UserNotificationPreference::defaults);
    }
}
