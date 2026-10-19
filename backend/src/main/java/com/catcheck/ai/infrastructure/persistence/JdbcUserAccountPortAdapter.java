package com.catcheck.ai.infrastructure.persistence;

import com.catcheck.ai.application.spi.AccountStatus;
import com.catcheck.ai.application.spi.UserAccountPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("aiJdbcUserAccountPortAdapter")
class JdbcUserAccountPortAdapter implements UserAccountPort {

    private final JdbcTemplate jdbc;

    JdbcUserAccountPortAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AccountStatus statusOf(UUID userId) {
        List<String> rows = jdbc.query(
                "SELECT status FROM app_user WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"),
                userId);
        return rows.isEmpty() ? null : AccountStatus.fromWire(rows.getFirst());
    }
}
