package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link UserIdentityRepository} tren {@code JdbcTemplate}. */
@Repository
public class JdbcUserIdentityRepository implements UserIdentityRepository {

    private static final String COLUMNS = """
            id, user_id, provider, provider_user_id, provider_email, email_verified,
            password_hash, linked_at, last_used_at, created_at, updated_at
            """;

    private static final String INSERT = """
            INSERT INTO user_identity (
                id, user_id, provider, provider_user_id, provider_email,
                email_verified, password_hash, linked_at, last_used_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcUserIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UserIdentity> findByUserId(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_identity WHERE user_id = ? ORDER BY linked_at",
                (rs, rowNum) -> map(rs), userId);
    }

    @Override
    public Optional<UserIdentity> findLocal(UUID userId) {
        return one("SELECT " + COLUMNS + " FROM user_identity WHERE user_id = ? AND provider = 'LOCAL'",
                userId);
    }

    @Override
    public Optional<UserIdentity> findByProviderSubject(IdentityProvider provider, String providerUserId) {
        return one("SELECT " + COLUMNS + " FROM user_identity WHERE provider = ? AND provider_user_id = ?",
                provider.name(), providerUserId);
    }

    @Override
    public boolean existsByProviderSubject(IdentityProvider provider, String providerUserId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM user_identity WHERE provider = ? AND provider_user_id = ?",
                Integer.class, provider.name(), providerUserId);
        return count != null && count > 0;
    }

    @Override
    public void insert(UserIdentity identity) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, identity.id());
            ps.setObject(i++, identity.userId());
            ps.setString(i++, identity.provider().name());
            ps.setString(i++, identity.providerUserId());
            ps.setString(i++, identity.providerEmail());
            ps.setBoolean(i++, identity.emailVerified());
            ps.setString(i++, identity.passwordHash());
            ps.setObject(i++, utc(identity.linkedAt()));
            ps.setObject(i++, utc(identity.lastUsedAt()));
            ps.setObject(i++, utc(identity.createdAt()));
            ps.setObject(i, utc(identity.updatedAt()));
            return ps;
        });
    }

    @Override
    public void touchLastUsed(UUID identityId, Instant lastUsedAt) {
        jdbc.update("UPDATE user_identity SET last_used_at = ?, updated_at = now() WHERE id = ?",
                utc(lastUsedAt), identityId);
    }

    @Override
    public boolean delete(UUID identityId) {
        return jdbc.update("DELETE FROM user_identity WHERE id = ?", identityId) > 0;
    }

    @Override
    public int countByUserId(UUID userId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM user_identity WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private Optional<UserIdentity> one(String sql, Object... args) {
        List<UserIdentity> rows = jdbc.query(sql, (rs, rowNum) -> map(rs), args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private UserIdentity map(ResultSet rs) throws SQLException {
        return new UserIdentity(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuid(rs, "user_id"),
                RowReaders.requiredEnum(rs, "provider", IdentityProvider.class),
                RowReaders.text(rs, "provider_user_id"),
                RowReaders.textOrNull(rs, "provider_email"),
                RowReaders.bool(rs, "email_verified"),
                RowReaders.textOrNull(rs, "password_hash"),
                RowReaders.requiredInstant(rs, "linked_at"),
                RowReaders.instant(rs, "last_used_at"),
                RowReaders.requiredInstant(rs, "created_at"),
                RowReaders.requiredInstant(rs, "updated_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
