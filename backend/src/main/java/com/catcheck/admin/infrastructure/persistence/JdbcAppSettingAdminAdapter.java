package com.catcheck.admin.infrastructure.persistence;

import com.catcheck.admin.domain.AppSettingRow;
import com.catcheck.admin.domain.port.AppSettingAdminPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAppSettingAdminAdapter implements AppSettingAdminPort {

    private static final String COLUMNS = """
            key, value #>> '{}' AS value_text, value_type, description, secret, updated_by, updated_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcAppSettingAdminAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AppSettingRow> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_setting ORDER BY key",
                (rs, rowNum) -> map(rs));
    }

    @Override
    public Optional<AppSettingRow> findByKey(String key) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_setting WHERE key = ?",
                (rs, rowNum) -> map(rs), key).stream().findFirst();
    }

    @Override
    public Optional<AppSettingRow> updateValue(String key, String rawValue, UUID updatedBy) {
        Optional<AppSettingRow> current = findByKey(key);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        String valueType = current.get().valueType() == null
                ? "STRING"
                : current.get().valueType().toUpperCase(Locale.ROOT);
        String valueExpression = switch (valueType) {
            case "INT" -> "to_jsonb(CAST(? AS numeric))";
            case "BOOL" -> "to_jsonb(CAST(? AS boolean))";
            case "JSON" -> "CAST(? AS jsonb)";
            default -> "to_jsonb(CAST(? AS text))";
        };
        int updated = jdbc.update("""
                UPDATE app_setting
                   SET value = %s, updated_by = ?
                 WHERE key = ?
                """.formatted(valueExpression), rawValue, updatedBy, key);
        return updated == 1 ? findByKey(key) : Optional.empty();
    }

    private static AppSettingRow map(ResultSet rs) throws SQLException {
        return new AppSettingRow(
                rs.getString("key"),
                rs.getString("value_text"),
                rs.getString("value_type"),
                rs.getString("description"),
                rs.getBoolean("secret"),
                rs.getObject("updated_by", UUID.class),
                instant(rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
