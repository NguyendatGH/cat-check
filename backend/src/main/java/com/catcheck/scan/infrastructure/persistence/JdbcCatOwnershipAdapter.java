package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.port.CatOwnershipPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiện thực {@link CatOwnershipPort} bằng JDBC thuần đọc trực tiếp bảng {@code cat} — KHÔNG entity
 * JPA, không import type Java của module {@code cat} (xem javadoc cổng và
 * {@code docs/handovers/A6.md} mục judgment call: module {@code cat} chưa công bố query port).
 */
@Repository
class JdbcCatOwnershipAdapter implements CatOwnershipPort {

    private static final String SQL = """
            SELECT id, owner_id, name, status, deleted_at
              FROM cat
             WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    JdbcCatOwnershipAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CatSnapshot> findSnapshot(UUID catId) {
        List<CatSnapshot> rows = jdbc.query(SQL, (rs, n) -> new CatSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("name"),
                "ARCHIVED".equals(rs.getString("status")),
                rs.getObject("deleted_at") != null
        ), catId);
        return rows.stream().findFirst();
    }
}
