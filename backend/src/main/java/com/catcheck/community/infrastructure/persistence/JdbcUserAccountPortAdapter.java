package com.catcheck.community.infrastructure.persistence;

import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link UserAccountPort} đọc trực tiếp cột {@code app_user.status} bằng {@code JdbcTemplate}.
 *
 * <p>Vì sao đọc thẳng bảng của module {@code identity} bằng SQL thô thay vì import type của
 * {@code identity}: {@code community/package-info.java} khai {@code allowedDependencies =
 * { "shared", "audit::api" }} — KHÔNG có {@code identity}. Đây CHÍNH XÁC là kỹ thuật
 * {@code cat.infrastructure.persistence.JdbcUserAccountPortAdapter} đã dùng và ghi chú công khai
 * (cùng kỹ thuật {@code scan/package-info.java} áp dụng cho quyền sở hữu mèo): đọc
 * {@code app_user} bằng JDBC read-only, KHÔNG import bất kỳ class nào của
 * {@code com.catcheck.identity.*} — không có cạnh phụ thuộc Java nào phát sinh nên
 * {@code ModularityTests} không bị ảnh hưởng.</p>
 *
 * <p>Bảng/cột tra ở {@code V5__identity.sql} (A1) — {@code app_user.status VARCHAR(24)} với đúng
 * sáu giá trị của {@link AccountStatus}. KHÔNG sửa V5, chỉ đọc.</p>
 */
@Repository("communityJdbcUserAccountPortAdapter")
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
