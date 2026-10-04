package com.catcheck.shared.infrastructure.persistence;

import com.catcheck.shared.application.spi.AppSettingWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Jdbc adapter cho các cấu hình runtime công khai mà admin được phép thay đổi. */
@Repository
class JdbcAppSettingWriter implements AppSettingWriter {

    private final JdbcTemplate jdbc;

    JdbcAppSettingWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Setting updateBoolean(String key, boolean value, String description, UUID updatedBy) {
        return update(key, Boolean.toString(value), "BOOL", description, updatedBy);
    }

    @Override
    public Setting updateString(String key, String value, String description, UUID updatedBy) {
        return update(key, value == null ? "" : value, "STRING", description, updatedBy);
    }

    private Setting update(String key, String value, String valueType, String description, UUID updatedBy) {
        jdbc.update("""
                INSERT INTO app_setting (key, value, value_type, description, secret, updated_by)
                VALUES (?, ?::jsonb, ?, ?, false, ?)
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, value_type = EXCLUDED.value_type,
                    description = EXCLUDED.description, secret = false, updated_by = EXCLUDED.updated_by,
                    updated_at = now()
                """, key, valueType.equals("BOOL") ? value : quoteJson(value), valueType, description, updatedBy);
        return jdbc.queryForObject("SELECT key, value #>> '{}' AS value_text, value_type, updated_at "
                        + "FROM app_setting WHERE key = ?",
                (rs, rowNum) -> new Setting(rs.getString("key"), rs.getString("value_text"),
                        rs.getString("value_type"), instant(rs.getObject("updated_at", OffsetDateTime.class))), key);
    }

    private static String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC).toInstant();
    }
}
