package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.OpenDsarPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcCatOpenDsarAdapter implements OpenDsarPort {

    private static final String EXISTS_OPEN = """
            SELECT EXISTS (
                SELECT 1 FROM dsar_request
                 WHERE user_id = ?
                   AND status NOT IN ('COMPLETED', 'REJECTED'))
            """;

    private final JdbcTemplate jdbc;

    JdbcCatOpenDsarAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasOpenRequestFor(UUID userId) {
        if (userId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS_OPEN, Boolean.class, userId));
    }
}
