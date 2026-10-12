package com.catcheck.notification.infrastructure.persistence;

import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

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
            return Map.of();
        }
    }

    static String toJson(JsonMapper mapper, Map<String, Object> payload) {
        return mapper.writeValueAsString(payload == null ? Map.of() : payload);
    }
}
