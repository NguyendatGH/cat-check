package com.catcheck.notification.infrastructure.persistence;

import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

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

    /**
     * Con trỏ keyset {@code (created_at, id)} — cùng khuôn với {@code JdbcScanQueryRepository}
     * và {@code HealthFlagRepositoryAdapter} để client chỉ phải hiểu một định dạng (p8 §8.1.4:
     * cursor là chuỗi opaque, client không tự sinh/tự phân tích).
     */
    record CursorKey(Instant createdAt, UUID id) {

        String encode() {
            String raw = createdAt.toEpochMilli() + "." + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        /** Trả {@code null} khi không có cursor; ném {@link IllegalArgumentException} khi cursor hỏng. */
        static CursorKey parse(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor),
                        java.nio.charset.StandardCharsets.UTF_8);
                int separator = raw.indexOf('.');
                if (separator <= 0) {
                    throw new IllegalArgumentException("cursor thiếu dấu phân cách");
                }
                return new CursorKey(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                        UUID.fromString(raw.substring(separator + 1)));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("cursor không hợp lệ", ex);
            }
        }
    }
}
