package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.OnboardingMilestone;
import com.catcheck.cat.application.spi.UserAccountPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link UserAccountPort} đọc trực tiếp cột {@code app_user.status} bằng {@code JdbcTemplate}.
 *
 * <p><b>Vì sao đọc thẳng bảng của module {@code identity} bằng SQL thô thay vì chờ A1 cấp
 * named interface</b>: đây là cổng SPI riêng của {@code cat} ({@code cat.application.spi}, KHÔNG
 * phải {@code identity.api}), và không có adapter nào cho nó thì Spring không đủ bean để khởi
 * tạo {@code CatProfileService}/{@code CatAvatarService} — toàn bộ module cat không khởi động
 * được. Đây CHÍNH XÁC là kỹ thuật {@code scan/package-info.java} đã dùng và ghi chú công khai:
 * "quyền sở hữu mèo... được đọc trực tiếp bằng JDBC read-only trên bảng {@code cat} trong
 * {@code scan.infrastructure.persistence} — KHÔNG import type nào của module {@code cat}". Ở đây
 * áp dụng ngược lại: cat đọc {@code app_user} mà KHÔNG import bất kỳ class nào của
 * {@code com.catcheck.identity.*} — không có cạnh phụ thuộc Java nào phát sinh nên
 * {@code ModularityTests} không bị ảnh hưởng.</p>
 *
 * <p>Bảng/cột tra ở {@code V5__identity.sql} (A1) — {@code app_user.status VARCHAR(24)} với đúng
 * sáu giá trị của {@link AccountStatus}. KHÔNG sửa V5, chỉ đọc.</p>
 */
@Repository
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

    /**
     * Chi tien, khong lui — so sanh bang vi tri trong mang thu tu ngay trong SQL nen thao tac
     * la nguyen tu (khong doc-roi-ghi, tranh dua giua hai request dong thoi). Cap nhat 0 dong
     * khi user da o cot moc bang hoac xa hon: dung y do, khong phai loi.
     */
    @Override
    public void advanceOnboardingStatus(UUID userId, OnboardingMilestone milestone) {
        jdbc.update(ADVANCE, milestone.wire(), userId, milestone.wire());
    }

    /**
     * Mang thu tu viet thang trong SQL (hang so, khong phai dau vao nguoi dung) thay vi bind
     * {@code String[]}: driver PostgreSQL khong tu suy duoc kieu SQL cho mang Java — cung ho loi
     * da gap that voi {@code java.time.Instant} ({@code Can't infer the SQL type to use}).
     */
    private static final String ADVANCE = """
            UPDATE app_user
               SET onboarding_status = ?
             WHERE id = ?
               AND array_position(
                       ARRAY['ACCOUNT_ONLY','CAT_CREATED','SURVEY_DONE_OR_SKIPPED','COMPLETED'],
                       onboarding_status)
                 < array_position(
                       ARRAY['ACCOUNT_ONLY','CAT_CREATED','SURVEY_DONE_OR_SKIPPED','COMPLETED'],
                       ?)
            """;
}
