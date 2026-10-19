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
