package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.UserActivityLog;
import com.catcheck.privacy.domain.port.UserActivityLogPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * {@link UserActivityLogPort} trên {@code JdbcTemplate}.
 *
 * <p>{@code props} là JSONB — KHÔNG chứa PII, KHÔNG chứa giá trị pH (bất biến I29, ép
 * bằng test quét ngược ở p17). Xoá cứng khi xoá tài khoản (p4 B11).</p>
 */
@Repository
public class JdbcUserActivityLogAdapter implements UserActivityLogPort {

    private static final String INSERT = """
            INSERT INTO user_activity_log (id, user_id, event_code, props, occurred_at, created_at)
            VALUES (?, ?, ?, ?::jsonb, ?, ?)
            """;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcUserActivityLogAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(UserActivityLog event) {
        String props;
        try {
            props = objectMapper.writeValueAsString(event.props());
        } catch (JacksonException ex) {
            throw new IllegalStateException("Ghi props của user_activity_log thất bại", ex);
        }
        jdbc.update(INSERT,
                event.id(),
                event.userId(),
                event.eventCode(),
                props,
                event.occurredAt(),
                event.createdAt());
    }

    @Override
    public int deleteByUser(UUID userId) {
        return jdbc.update("DELETE FROM user_activity_log WHERE user_id = ?", userId);
    }
}
