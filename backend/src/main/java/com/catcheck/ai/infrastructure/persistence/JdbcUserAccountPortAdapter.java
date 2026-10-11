package com.catcheck.ai.infrastructure.persistence;

import com.catcheck.ai.application.spi.AccountStatus;
import com.catcheck.ai.application.spi.UserAccountPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link UserAccountPort} đọc trực tiếp {@code app_user.status} (V5, chỉ đọc) — cùng kỹ thuật
 * {@code community.infrastructure.persistence.JdbcUserAccountPortAdapter}: không import lớp nào của
 * {@code identity} nên không phát sinh cạnh phụ thuộc module.
 *
 * <p>Tên bean tường minh: hai lớp cùng tên ở {@code cat}/{@code community} đã chiếm/đặt tên bean
 * riêng; để tên mặc định sẽ trùng {@code jdbcUserAccountPortAdapter} và app không khởi động.</p>
 */
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
