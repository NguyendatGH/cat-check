package com.catcheck.shared.infrastructure.persistence;

import com.catcheck.shared.application.spi.AppSettingReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link AppSettingReader} trên {@code JdbcTemplate}.
 *
 * <p><b>Luôn lọc {@code secret = false}.</b> Cổng này phục vụ endpoint CÔNG KHAI K2, nên một
 * khoá bị đánh dấu nhạy cảm không bao giờ được rò ra ngoài kể cả khi gọi đúng tên khoá.</p>
 *
 * <p>{@code value} là JSONB: {@code value #>> '{}'} trả về text thô của giá trị vô hướng
 * (bỏ dấu nháy kép của JSON string) — cùng cách {@code cat.infrastructure.persistence
 * .JdbcAppSettingAdapter} đang đọc.</p>
 */
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
