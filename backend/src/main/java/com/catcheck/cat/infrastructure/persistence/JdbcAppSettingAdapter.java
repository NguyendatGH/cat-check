package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.AppSettingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

@Repository
class JdbcAppSettingAdapter implements AppSettingPort {

    private final JdbcTemplate jdbc;

    JdbcAppSettingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt findInt(String key, UUID requesterId) {
        List<String> rows = jdbc.query(
                "SELECT value #>> '{}' AS value_text FROM app_setting WHERE key = ? AND value_type = 'INT'",
                (rs, rowNum) -> rs.getString("value_text"),
                key);
        if (rows.isEmpty() || rows.getFirst() == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(rows.getFirst().trim()));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }
}
