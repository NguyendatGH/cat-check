package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.AttentionAlertChannel;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.StorageProvider;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.UserAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link UserAccountRepository} tren {@code JdbcTemplate}.
 *
 * <p>Khong dung Spring Data hay JPA (R7 cho phep Spring Data o day nhung tai khoan
 * khong can: khong co {@code @Entity}, khong co quan he, va co vai dong CHECK
 * rang buoc nghiem ngat ma ORM se sinh lai sai).</p>
 */
@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

    private static final String COLUMNS = """
            id, email, email_verified_at, full_name, phone, phone_key_version,
            avatar_storage_key, avatar_storage_provider, locale, timezone,
            status, onboarding_status, failed_login_count, locked_until,
            password_changed_at, force_password_reset_at, processing_restricted_at,
            pseudonym_id, notification_prefs, referral_code_raw, last_login_at,
            deletion_scheduled_at, anonymized_at, created_at, updated_at
            """;

    private static final String INSERT = """
            INSERT INTO app_user (
                id, email, email_verified_at, full_name, phone, phone_key_version,
                locale, timezone, status, onboarding_status, notification_prefs,
                password_changed_at, pseudonym_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """;

    /**
     * p8 B11 chi dich danh bang {@code user_notification_preference} (p4 F4, tao o V13) la
     * nguon cua tuy chon thong bao — KHONG phai cot JSONB {@code app_user.notification_prefs}.
     * Ban truoc doc JSONB vi luc do V13 chua ton tai; nay da co bang that nen doc thang cot,
     * khong con phai chong do kieu du lieu rac cua JSONB.
     */
    private static final String SELECT_NOTIFICATION_PREFS = """
            SELECT attention_alert_channel, credit_alerts_enabled, report_ready_enabled,
                   image_retention_warning_enabled, normal_result_enabled,
                   quiet_hours_enabled, quiet_hours_start, quiet_hours_end
              FROM user_notification_preference
             WHERE user_id = ?
            """;

    /**
     * Upsert thay vi UPDATE: p4 F4 noi dong nay duoc tao cung luc voi {@code app_user}, nhung
     * nhung tai khoan dang ky TRUOC khi V13 chay thi chua co dong nao. Upsert mot cau lenh
     * nen hai request PUT song song khong de mat update cua nhau.
     */
    private static final String UPSERT_NOTIFICATION_PREFS = """
            INSERT INTO user_notification_preference (
                user_id, attention_alert_channel, credit_alerts_enabled, report_ready_enabled,
                image_retention_warning_enabled, normal_result_enabled,
                quiet_hours_enabled, quiet_hours_start, quiet_hours_end, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (user_id) DO UPDATE SET
                attention_alert_channel         = EXCLUDED.attention_alert_channel,
                credit_alerts_enabled           = EXCLUDED.credit_alerts_enabled,
                report_ready_enabled            = EXCLUDED.report_ready_enabled,
                image_retention_warning_enabled = EXCLUDED.image_retention_warning_enabled,
                normal_result_enabled           = EXCLUDED.normal_result_enabled,
                quiet_hours_enabled             = EXCLUDED.quiet_hours_enabled,
                quiet_hours_start               = EXCLUDED.quiet_hours_start,
                quiet_hours_end                 = EXCLUDED.quiet_hours_end,
                updated_at                      = now()
            """;

    private final JdbcTemplate jdbc;

    public JdbcUserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return queryOne("SELECT " + COLUMNS + " FROM app_user WHERE id = ?", userId);
    }

    @Override
    public Optional<UserAccount> findByEmail(EmailAddress email) {
        return queryOne("SELECT " + COLUMNS + " FROM app_user WHERE email = ?", email.value());
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email.value());
        return count != null && count > 0;
    }

    @Override
    public void insert(UserAccount account) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, account.id());
            ps.setString(i++, account.email().value());
            setInstant(ps, i++, account.emailVerifiedAt());
            ps.setString(i++, account.fullName());
            setBytes(ps, i++, account.phone());
            setInteger(ps, i++, account.phoneKeyVersion());
            ps.setString(i++, account.locale().code());
            ps.setString(i++, account.timezone());
            ps.setString(i++, account.status().name());
            ps.setString(i++, account.onboardingStatus().name());
            ps.setString(i++, account.notificationPrefsJson() == null ? "{}" : account.notificationPrefsJson());
            setInstant(ps, i++, account.passwordChangedAt());
            setUuid(ps, i++, account.pseudonymId());
            ps.setObject(i++, utc(account.createdAt()));
            ps.setObject(i, utc(account.updatedAt()));
            return ps;
        });
    }

    @Override
    public void markEmailVerified(UUID userId, Instant verifiedAt) {
        // Bug that da sua: chi doi email_verified_at, KHONG doi status — hai noi goi ham nay
        // (RegistrationService.completePreverifiedRegistration/activateWithTicket) deu la
        // luc kich hoat tai khoan MOI dang ky lan dau (chi 2 noi goi trong toan bo codebase,
        // xac nhan bang grep), nen tai khoan bi ket cung o PENDING_VERIFICATION mai mai — dang
        // nhap lai sau khi "kich hoat" thanh cong van bi tu choi ACCOUNT_RESTRICTED (LoginService
        // chi cho status=ACTIVE di qua). Xac nhan that: dang ky -> verify -> kich hoat (tra
        // authenticated=true) -> logout -> login lai bang dung mat khau -> 403
        // ACCOUNT_RESTRICTED. Doi status sang ACTIVE cung luc la dung ngu nghia duy nhat ham
        // nay tung duoc goi.
        jdbc.update("UPDATE app_user SET email_verified_at = ?, status = 'ACTIVE', updated_at = now() WHERE id = ?",
                utc(verifiedAt), userId);
    }

    @Override
    public void updateEmail(UUID userId, String newEmail, Instant changedAt) {
        // Email la khoa chinh: UNIQUE(email) + citext. Chi goi sau khi OTP
        // EMAIL_CHANGE da xac minh tren dia chi moi (p8 B8).
        jdbc.update("UPDATE app_user SET email = ?, updated_at = now() WHERE id = ?",
                newEmail, userId);
    }

    @Override
    public void updateLoginState(UUID userId, UserStatus status, int failedLoginCount,
                                 Instant lockedUntil, Instant lastLoginAt) {
        // KHONG cham vao processing_restricted_at / deletion_scheduled_at / anonymized_at:
        // p4 §A1 rang buoc CHECK chung cho status, va tang nghiep vu chi doi status
        // sang LOCKED/ACTIVE nen ghi NULL o day se lam mat rang buoc cua dong do.
        jdbc.update("""
                UPDATE app_user
                   SET status = ?, failed_login_count = ?, locked_until = ?,
                       last_login_at = ?, updated_at = now()
                 WHERE id = ?
                """,
                status.name(), failedLoginCount, utc(lockedUntil), utc(lastLoginAt), userId);
    }

    @Override
    public void updateRestrictionState(UUID userId, UserStatus status,
                                       Instant processingRestrictedAt,
                                       Instant deletionScheduledAt,
                                       Instant anonymizedAt) {
        jdbc.update("""
                UPDATE app_user
                   SET status = ?, processing_restricted_at = ?,
                       deletion_scheduled_at = ?, anonymized_at = ?, updated_at = now()
                 WHERE id = ?
                """,
                status.name(), utc(processingRestrictedAt), utc(deletionScheduledAt),
                utc(anonymizedAt), userId);
    }

    @Override
    public void updatePasswordHash(UUID userId, String passwordHash) {
        jdbc.update("UPDATE user_identity SET password_hash = ?, updated_at = now()"
                        + " WHERE user_id = ? AND provider = 'LOCAL'",
                passwordHash, userId);
    }

    @Override
    public void updatePasswordTimestamps(UUID userId, Instant passwordChangedAt, Instant forcePasswordResetAt) {
        jdbc.update("""
                UPDATE app_user
                   SET password_changed_at = ?, force_password_reset_at = ?, updated_at = now()
                 WHERE id = ?
                """,
                utc(passwordChangedAt), utc(forcePasswordResetAt), userId);
    }

    @Override
    public void updateProfile(UUID userId, String fullName, byte[] phone, Integer phoneKeyVersion,
                              AppLocale locale, String timezone) {
        // `phone` va `phone_key_version` ghi ca cap: CHECK ck_app_user_phone_pair
        // khong cho phep doi lai le.
        jdbc.update("""
                UPDATE app_user
                   SET full_name = ?, phone = ?, phone_key_version = ?,
                       locale = ?, timezone = ?, updated_at = now()
                 WHERE id = ?
                """,
                fullName, phone, phoneKeyVersion, locale.code(), timezone, userId);
    }

    @Override
    public void updateAvatar(UUID userId, String storageKey, StorageProvider provider) {
        jdbc.update("""
                UPDATE app_user
                   SET avatar_storage_key = ?, avatar_storage_provider = ?, updated_at = now()
                 WHERE id = ?
                """,
                storageKey, provider == null ? null : provider.name(), userId);
    }

    @Override
    public Optional<NotificationPreferences> findNotificationPreferences(UUID userId) {
        List<NotificationPreferences> rows = jdbc.query(SELECT_NOTIFICATION_PREFS,
                (rs, rowNum) -> mapNotificationPreferences(rs), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void updateNotificationPreferences(UUID userId, NotificationPreferences p) {
        jdbc.update(UPSERT_NOTIFICATION_PREFS,
                userId,
                p.attentionAlertChannel().name(),
                p.creditAlertsEnabled(),
                p.reportReadyEnabled(),
                p.imageRetentionWarningEnabled(),
                p.normalResultEnabled(),
                p.quietHoursEnabled(),
                p.quietHoursStart(),
                p.quietHoursEnd());
    }

    /** Lay danh sach user theo paging co khoa (dung cho job, khong dung o request luong). */
    public List<UserAccount> findPage(UUID beforeId, Instant beforeCreatedAt, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_user"
                        + " WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, rowNum) -> map(rs), beforeCreatedAt, beforeId, limit);
    }

    private Optional<UserAccount> queryOne(String sql, Object... args) {
        List<UserAccount> rows = jdbc.query(sql, (rs, rowNum) -> map(rs), args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private UserAccount map(java.sql.ResultSet rs) throws SQLException {
        return new UserAccount(
                RowReaders.uuid(rs, "id"),
                EmailAddress.of(RowReaders.text(rs, "email")),
                RowReaders.instant(rs, "email_verified_at"),
                RowReaders.text(rs, "full_name"),
                RowReaders.bytesOrNull(rs, "phone"),
                RowReaders.integerOrNull(rs, "phone_key_version"),
                RowReaders.textOrNull(rs, "avatar_storage_key"),
                RowReaders.enumValue(rs, "avatar_storage_provider", StorageProvider.class),
                AppLocale.fromCode(RowReaders.text(rs, "locale")),
                RowReaders.text(rs, "timezone"),
                RowReaders.requiredEnum(rs, "status", UserStatus.class),
                RowReaders.requiredEnum(rs, "onboarding_status", OnboardingStatus.class),
                RowReaders.requiredInt(rs, "failed_login_count"),
                RowReaders.instant(rs, "locked_until"),
                RowReaders.instant(rs, "password_changed_at"),
                RowReaders.instant(rs, "force_password_reset_at"),
                RowReaders.instant(rs, "processing_restricted_at"),
                RowReaders.uuidOrNull(rs, "pseudonym_id"),
                RowReaders.textOrNull(rs, "notification_prefs"),
                RowReaders.textOrNull(rs, "referral_code_raw"),
                RowReaders.instant(rs, "last_login_at"),
                RowReaders.instant(rs, "deletion_scheduled_at"),
                RowReaders.instant(rs, "anonymized_at"),
                RowReaders.requiredInstant(rs, "created_at"),
                RowReaders.requiredInstant(rs, "updated_at"));
    }

    private static NotificationPreferences mapNotificationPreferences(java.sql.ResultSet rs)
            throws SQLException {
        NotificationPreferences fallback = NotificationPreferences.defaults();
        String channel = rs.getString("attention_alert_channel");
        return new NotificationPreferences(
                channel == null
                        ? fallback.attentionAlertChannel()
                        : AttentionAlertChannel.valueOf(channel),
                rs.getBoolean("credit_alerts_enabled"),
                rs.getBoolean("report_ready_enabled"),
                rs.getBoolean("image_retention_warning_enabled"),
                rs.getBoolean("normal_result_enabled"),
                rs.getBoolean("quiet_hours_enabled"),
                rs.getObject("quiet_hours_start", java.time.LocalTime.class),
                rs.getObject("quiet_hours_end", java.time.LocalTime.class));
    }

    /** Mot bieu thuc {@code SELECT} doc mot co boolean trong {@code notification_prefs}. */
    private static String jsonBool(String jsonPath, String alias) {
        return ("CASE WHEN jsonb_typeof(notification_prefs #> '{%s}') = 'boolean' "
                + "THEN (notification_prefs #>> '{%s}')::boolean END AS %s")
                .formatted(jsonPath, jsonPath, alias);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setObject(index, utc(value));
        }
    }

    private static void setBytes(PreparedStatement ps, int index, byte[] value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BINARY);
        } else {
            ps.setBytes(index, value);
        }
    }

    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.SMALLINT);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
