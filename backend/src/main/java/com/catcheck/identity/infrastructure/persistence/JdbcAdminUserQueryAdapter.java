package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AdminUserQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link AdminUserQueryPort} trên {@code JdbcTemplate} — L1, L2.
 *
 * <p><b>Đếm bằng truy vấn con tương quan, không bằng {@code JOIN} + {@code GROUP BY}:</b>
 * {@code JOIN cat} rồi {@code COUNT(DISTINCT ...)} nhân dòng lên rồi phải dedupe, và khi có hai
 * bảng đếm thì tích Descartes giữa {@code cat} và {@code scan} làm con số sai (một user 2 mèo
 * 30 scan ra {@code catCount = 2 × 30}). Truy vấn con dùng đúng index
 * {@code cat(owner_id)}/{@code scan(user_id)} của hai bảng đó. Hồ sơ mèo đã xoá mềm
 * ({@code deleted_at IS NOT NULL}) không được tính — cột "số mèo" của p14 là số hồ sơ đang
 * dùng, không phải số dòng còn trong bảng.</p>
 *
 * <p><b>Ba bảng ngoài module</b> ({@code cat}, {@code scan}, {@code user_entitlement}) được đọc
 * trực tiếp ở đây — lệch so với nguyên tắc "mỗi module sở hữu bảng của mình" của p7 §7.2.3. Lý
 * do và hướng sửa: xem javadoc {@link AdminUserQueryPort} và handoff H15.103.</p>
 */
@Repository
public class JdbcAdminUserQueryAdapter implements AdminUserQueryPort {

    private static final String SELECT = """
            SELECT u.id, u.email, u.full_name, u.status, u.onboarding_status,
                   u.email_verified_at, u.created_at, u.last_login_at, u.locked_until,
                   (SELECT COUNT(*) FROM cat c
                     WHERE c.owner_id = u.id AND c.deleted_at IS NULL)        AS cat_count,
                   (SELECT COUNT(*) FROM scan s WHERE s.user_id = u.id)       AS scan_count,
                   (SELECT e.highest_package FROM user_entitlement e
                     WHERE e.user_id = u.id)                                  AS highest_package
              FROM app_user u
            """;

    private final JdbcTemplate jdbc;

    public JdbcAdminUserQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminUserSummary> search(UserStatus status, String email, String highestPackage,
                                         int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = whereOf(status, email, highestPackage, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(SELECT + where + " ORDER BY u.created_at DESC, u.id LIMIT ? OFFSET ?",
                this::map, args.toArray());
    }

    @Override
    public long count(UserStatus status, String email, String highestPackage) {
        List<Object> args = new ArrayList<>();
        String where = whereOf(status, email, highestPackage, args);
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user u
                """ + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public Optional<AdminUserSummary> findById(UUID userId) {
        return jdbc.query(SELECT + " WHERE u.id = ?", this::map, userId).stream().findFirst();
    }

    /**
     * {@code u.email = ?} chứ không {@code LIKE}: cột là {@code CITEXT} nên so sánh đã không
     * phân biệt hoa thường (p4 §A1), và p14 §14.3.2 mục 2 cố ý <b>chỉ cho khớp chính xác</b>.
     * Lọc {@code highest_package} phải lặp lại truy vấn con vì alias của SELECT không dùng được
     * trong WHERE của cùng mức.
     */
    private static String whereOf(UserStatus status, String email, String highestPackage,
                                  List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (status != null) {
            append(where, "u.status = ?");
            args.add(status.name());
        }
        if (email != null && !email.isBlank()) {
            append(where, "u.email = ?");
            args.add(email.strip());
        }
        if (highestPackage != null && !highestPackage.isBlank()) {
            append(where, "EXISTS (SELECT 1 FROM user_entitlement e"
                    + " WHERE e.user_id = u.id AND e.highest_package = ?)");
            args.add(highestPackage.strip());
        }
        return where.toString();
    }

    private static void append(StringBuilder where, String condition) {
        where.append(where.isEmpty() ? " WHERE " : " AND ").append(condition);
    }

    private AdminUserSummary map(ResultSet rs, int rowNum) throws SQLException {
        return new AdminUserSummary(
                RowReaders.uuid(rs, "id"),
                RowReaders.text(rs, "email"),
                RowReaders.text(rs, "full_name"),
                RowReaders.requiredEnum(rs, "status", UserStatus.class),
                RowReaders.requiredEnum(rs, "onboarding_status", OnboardingStatus.class),
                RowReaders.instant(rs, "email_verified_at"),
                RowReaders.requiredInstant(rs, "created_at"),
                RowReaders.instant(rs, "last_login_at"),
                RowReaders.instant(rs, "locked_until"),
                rs.getLong("cat_count"),
                rs.getLong("scan_count"),
                RowReaders.textOrNull(rs, "highest_package"));
    }
}
