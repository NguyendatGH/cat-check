package com.catcheck.shared.infrastructure.persistence;

import com.catcheck.shared.application.spi.AppSettingReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
class JdbcAppSettingReader implements AppSettingReader {

    private final JdbcTemplate jdbc;

    JdbcAppSettingReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findPublicValue(String key) {
        return jdbc.query(
                "SELECT value #>> '{}' AS value_text FROM app_setting WHERE key = ? AND secret = false",
                (rs, rowNum) -> rs.getString("value_text"),
                key).stream().findFirst();
    }

    @Override
    public Map<String, String> findPublicNamespace(String namespacePrefix) {
        Map<String, String> result = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT key, value #>> '{}' AS value_text
                  FROM app_setting
                 WHERE secret = false AND key LIKE ?
                 ORDER BY key
                """,
                rs -> {
                    String key = rs.getString("key");
                    result.put(key.substring(namespacePrefix.length() + 1), rs.getString("value_text"));
                },
                namespacePrefix + ".%");
        return result;
    }
}
