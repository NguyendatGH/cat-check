package com.catcheck.notification.infrastructure.persistence;

import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Tiện ích đọc/ghi dùng chung cho các repository của module.
 *
 * <p>Hai thứ dễ làm sai, giống hệt ghi chú ở {@code JdbcReminderRepository}:</p>
 * <ul>
 *   <li>Cột {@code TIMESTAMPTZ} phải đọc bằng {@code rs.getObject(col, OffsetDateTime.class)},
 *       KHÔNG {@code rs.getTimestamp()} — ArchUnit R13 cấm {@code java.sql.Timestamp}.</li>
 *   <li>Cột {@code JSONB} khi ghi phải ép {@code ?::jsonb}, nếu không driver gửi kiểu
 *       {@code text} và Postgres từ chối.</li>
 * </ul>
 */
final class NotificationRowReaders {

    private NotificationRowReaders() {
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> json(JsonMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(raw, Map.class);
        } catch (RuntimeException ex) {
            // Payload hỏng không được làm chết inbox của user — trả rỗng, nội dung hiển thị đã
            // nằm ở title_snapshot/body_snapshot.
            return Map.of();
        }
    }

    static String toJson(JsonMapper mapper, Map<String, Object> payload) {
        return mapper.writeValueAsString(payload == null ? Map.of() : payload);
    }
}
