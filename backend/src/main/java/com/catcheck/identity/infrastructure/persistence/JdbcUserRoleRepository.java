package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.port.UserRoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** {@link UserRoleRepository} tren {@code JdbcTemplate}. */
@Repository
public class JdbcUserRoleRepository implements UserRoleRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserRole> findByUserId(UUID userId) {
        return jdbc.query("SELECT role FROM user_role WHERE user_id = ? ORDER BY role",
                (rs, rowNum) -> UserRole.valueOf(rs.getString("role")), userId);
    }

    /**
     * Doc thang ra {@link Set}{@code <UserRole>} cho nhu cau bootstrap phien
     * (p8 A2) va kiem tra quyen tai cho — goi ham nay de tranh phai trai
     * danh sach o nhieu noi.
     */
    public Set<UserRole> findRoleSetByUserId(UUID userId) {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        // Ep sang RowCallbackHandler: lambda `rs -> ...` bi mo ambiguous giua
        // ResultSetExtractor va RowCallbackHandler (ca hai 1 tham so).
        jdbc.query("SELECT role FROM user_role WHERE user_id = ?",
                (RowCallbackHandler) rs -> roles.add(UserRole.valueOf(rs.getString("role"))),
                userId);
        return roles;
    }

    @Override
    public void grant(UUID userId, UserRole role, UUID grantedBy) {
        // ON CONFLICT DO NOTHING: cap lai role da co khong phai loi (p8 khong quy dinh
        // 409 o day) va khong ghi deo granted_at goc.
        jdbc.update("""
                INSERT INTO user_role (user_id, role, granted_by)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id, role) DO NOTHING
                """,
                userId, role.name(), grantedBy);
    }

    @Override
    public void revoke(UUID userId, UserRole role) {
        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = ?", userId, role.name());
    }
}
