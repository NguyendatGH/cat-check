package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.Base32;
import com.catcheck.identity.domain.MfaRecoveryCode;
import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.identity.domain.TotpCodeVerifier;
import com.catcheck.identity.domain.TotpStatus;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.port.MfaRecoveryCodeRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.RecoveryCodeHasher;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * TOTP (RFC 6238) va ma khoi phuc — p8 B14..B18, A10, A11; p11 §11.12.
 *
 * <p>Secret TOTP duoc ma hoa AES-256-GCM khoa con {@code mfa.totp} truoc khi ghi
 * xuong {@code user_mfa_totp.secret_enc} (p11 §11.10.3). Ban tho chi ton tai trong
 * response cua {@code init} — {@code PENDING} ghi xuong DB da ma hoa, khong giu
 * trong session (p11 §11.12.3: session attribute bi serialize thang xuong
 * {@code SPRING_SESSION_ATTRIBUTES}).</p>
 *
 * <p>Ma khoi phuc: 10 ma Crockford Base32, hien thi {@code XXXXX-XXXXX}, <b>chi tra
 * dung mot lan</b> khi enroll/reset. Bam {@code HMAC-SHA256(recovery_pepper, code)}
 * (p11 §11.12.3) — khong phai BCrypt.</p>
 */
@Service
public class MfaService {

    /** p11 §11.12.3: 10 ma mot lan khi enroll. */
    public static final int RECOVERY_CODE_BATCH_SIZE = 10;

    /** p11 §11.12.3: ban ghi PENDING song 10 phut. */
    public static final Duration PENDING_TTL = Duration.ofMinutes(10);

    /** p11 §11.12.3: khoa buoc MFA 15 phut sai 5 lan / 5 phut. */
    public static final int TOTP_MAX_FAILED = 5;
    public static final Duration TOTP_LOCK_DURATION = Duration.ofMinutes(15);

    private static final String TOTP_TABLE = "user_mfa_totp";
    private static final String TOTP_SECRET_COLUMN = "secret_enc";

    private final MfaTotpRepository totpRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final UserRoleRepository roleRepository;
    private final PiiCipher piiCipher;
    private final RecoveryCodeHasher recoveryCodeHasher;
    private final RateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MfaService(MfaTotpRepository totpRepository,
                      MfaRecoveryCodeRepository recoveryCodeRepository,
                      UserRoleRepository roleRepository,
                      PiiCipher piiCipher,
                      RecoveryCodeHasher recoveryCodeHasher,
                      RateLimiter rateLimiter,
                      AuditLogService auditLogService,
                      Clock clock) {
        this.totpRepository = totpRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.roleRepository = roleRepository;
        this.piiCipher = piiCipher;
        this.recoveryCodeHasher = recoveryCodeHasher;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Ket qua {@code POST /account/mfa/totp/init} — secret chi tra o day, khong luu log. */
    public record Enrollment(
            String secretBase32,
            String otpauthUri,
            Instant expiresAt,
            int digits,
            int periodSeconds,
            String algorithm) {
    }

    /** Ket qua {@code POST /account/mfa/totp/confirm} — 10 ma khoi phuc, chi doc mot lan. */
    public record Confirmation(
            List<String> recoveryCodes,
            Instant activatedAt) {
    }

    /** Trang thái TOTP cho {@code GET /account/mfa/totp} (p8 B14). */
    public record TotpStatusView(
            TotpStatus status,
            Instant activatedAt,
            long recoveryCodesRemaining,
            Instant lockedUntil) {

        public static TotpStatusView of(MfaTotp totp, long recoveryCodesRemaining) {
            return new TotpStatusView(totp.status(), totp.activatedAt(), recoveryCodesRemaining,
                    totp.lockedUntil());
        }
    }

    /**
     * B15 — sinh secret {@code PENDING} + {@code otpauthUri}. SPA tu ve QR tu URI;
     * server KHONG sinh anh QR (p8 §8.5.2).
     */
    @Transactional
    public Enrollment init(UUID userId, String email, AuthRequestContext context) {
        requireRateLimit(RateLimitRule.MFA_INIT, userId.toString());

        // PENDING cu bi thay boi PENDING moi — khong co cai nao mat (p11 §11.12.3).
        totpRepository.delete(userId);

        String secretBase32 = Base32.newTotpSecret();
        Instant now = clock.instant();
        byte[] secretEnc = piiCipher.encrypt(CryptoPurpose.MFA_TOTP,
                PiiCipher.aad(TOTP_TABLE, TOTP_SECRET_COLUMN, userId),
                secretBase32.getBytes(StandardCharsets.UTF_8));

        totpRepository.insert(new MfaTotp(
                userId,
                secretEnc,
                TotpStatus.PENDING,
                TotpCodeVerifier.DEFAULT_ALGORITHM,
                6,
                30,
                null,
                0,
                null,
                now.plus(PENDING_TTL),
                null,
                piiCipher.currentWriteKeyVersion(),
                null,
                null,
                now,
                now));

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("MFA_TOTP_INIT")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new Enrollment(
                secretBase32,
                otpauthUri(email, secretBase32, TotpCodeVerifier.DEFAULT_ALGORITHM, 6, 30),
                now.plus(PENDING_TTL),
                6,
                30,
                TotpCodeVerifier.DEFAULT_ALGORITHM);
    }

    /**
     * B16 — xac nhan ma lan dau tung => {@code ACTIVE} + 10 ma khoi phuc.
     * 10 ma tra <b>dung mot lan</b>, khong co endpoint nao doc lai (p8 §8.5.2).
     */
    @Transactional
    public Confirmation confirm(UUID userId, String code, AuthRequestContext context) {
        requireRateLimit(RateLimitRule.MFA_CONFIRM, userId.toString());

        MfaTotp totp = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED));
        if (totp.status() == TotpStatus.ACTIVE) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_ALREADY_ENABLED);
        }
        if (totp.pendingExpiresAt() == null || totp.pendingExpiresAt().isBefore(clock.instant())) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_ENROLLMENT_EXPIRED);
        }

        String secretBase32 = decryptSecret(userId, totp);
        long currentStep = clock.instant().getEpochSecond() / totp.periodSeconds();
        if (!TotpCodeVerifier.verify(totp.algorithm(), totp.digits(),
                secretBase32, code, totp.lastUsedStep() == null ? -1 : totp.lastUsedStep(), currentStep)) {
            int failed = totpRepository.incrementFailedCount(userId);
            if (failed >= TOTP_MAX_FAILED) {
                totpRepository.lock(userId, clock.instant().plus(TOTP_LOCK_DURATION));
                auditDenied(userId, "totp_enroll_failed", context);
                throw new RateLimitedException(IdentityErrorCode.TOTP_LOCKED,
                        TOTP_LOCK_DURATION.getSeconds());
            }
            auditDenied(userId, "totp_enroll_wrong_code", context);
            throw new BusinessRuleException(IdentityErrorCode.TOTP_INVALID,
                    TOTP_MAX_FAILED - failed);
        }

        Instant now = clock.instant();
        totpRepository.markActive(userId, now, currentStep);

        List<String> recoveryCodes = generateRecoveryCodes(userId, now);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("MFA_TOTP_ENROLLED")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new Confirmation(recoveryCodes, now);
    }

    /** A10 — buoc 2 khi dang nhap: kiem tra ma TOTP, tra ve buoc thoi gian da dung. */
    @Transactional
    public long verifyForLogin(UUID userId, String code, AuthRequestContext context) {
        requireRateLimit(RateLimitRule.MFA_VERIFY, userId.toString());

        MfaTotp totp = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED));
        if (totp.status() != TotpStatus.ACTIVE) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED);
        }
        if (totp.lockedUntil() != null && totp.lockedUntil().isAfter(clock.instant())) {
            throw new RateLimitedException(IdentityErrorCode.TOTP_LOCKED,
                    Duration.between(clock.instant(), totp.lockedUntil()).getSeconds());
        }

        String secretBase32 = decryptSecret(userId, totp);
        long currentStep = clock.instant().getEpochSecond() / totp.periodSeconds();
        long lastStep = totp.lastUsedStep() == null ? -1 : totp.lastUsedStep();
        if (!TotpCodeVerifier.verify(totp.algorithm(), totp.digits(),
                secretBase32, code, lastStep, currentStep)) {
            int failed = totpRepository.incrementFailedCount(userId);
            if (failed >= TOTP_MAX_FAILED) {
                totpRepository.lock(userId, clock.instant().plus(TOTP_LOCK_DURATION));
                auditDenied(userId, "totp_login_failed", context);
                throw new RateLimitedException(IdentityErrorCode.TOTP_LOCKED,
                        TOTP_LOCK_DURATION.getSeconds());
            }
            auditDenied(userId, "totp_login_wrong_code", context);
            throw new BusinessRuleException(IdentityErrorCode.TOTP_INVALID,
                    TOTP_MAX_FAILED - failed);
        }

        totpRepository.resetFailedCount(userId);
        totpRepository.updateLastUsedStep(userId, currentStep);
        return currentStep;
    }

    /**
     * A11 — dung ma khoi phuc thay ma TOTP. Cho qua buoc MFA <strong>va</strong> bat
     * buoc dan toi enroll lai thiet bi trong phien do (p4 §A8: ma khoi phuc la loi
     * thoat hiem, khong phai phuong thuc dang nhap the thay).
     */
    @Transactional
    public RecoveryResult verifyRecovery(UUID userId, String recoveryCode, AuthRequestContext context) {
        requireRateLimit(RateLimitRule.MFA_RECOVERY, userId.toString());

        MfaTotp totp = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED));
        if (totp.status() != TotpStatus.ACTIVE) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED);
        }

        List<MfaRecoveryCode> unused = recoveryCodeRepository.findUnusedByUserId(userId);
        if (unused.isEmpty()) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_RECOVERY_EXHAUSTED);
        }

        String normalized = recoveryCode == null ? "" : recoveryCode.trim().toUpperCase(java.util.Locale.ROOT);
        MfaRecoveryCode matched = null;
        for (MfaRecoveryCode candidate : unused) {
            if (recoveryCodeHasher.matches(normalized, candidate.codeHash(), candidate.pepperVersion())) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) {
            auditDenied(userId, "recovery_code_wrong", context);
            throw new BusinessRuleException(IdentityErrorCode.TOTP_RECOVERY_INVALID,
                    unused.size(), unused.size());
        }

        recoveryCodeRepository.markUsed(matched.id(), userId, clock.instant(), context.ipAddress());
        long remaining = recoveryCodeRepository.countUnusedByUserId(userId);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("MFA_RECOVERY_USED")
                .meta("remaining", remaining)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new RecoveryResult(remaining, true);
    }

    /** B17 — sinh lai 10 ma, huyn tron lo cu (p11 §11.12.3). */
    @Transactional
    public Confirmation regenerateRecoveryCodes(UUID userId, AuthRequestContext context) {
        requireRateLimit(RateLimitRule.MFA_REGENERATE, userId.toString());

        MfaTotp totp = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED));
        if (totp.status() != TotpStatus.ACTIVE) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED);
        }

        recoveryCodeRepository.deleteByUserId(userId);
        List<String> recoveryCodes = generateRecoveryCodes(userId, clock.instant());

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("MFA_RECOVERY_REGENERATED")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new Confirmation(recoveryCodes, totp.activatedAt());
    }

    /** B18 — tu go TOTP, CHI khi tai khoan khong con role admin (p11 §11.12.3). */
    @Transactional
    public void delete(UUID userId, AuthRequestContext context) {
        MfaTotp totp = totpRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED));
        if (totp.status() != TotpStatus.ACTIVE) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_NOT_ENABLED);
        }

        boolean hasAdminRole = roleRepository.findByUserId(userId).stream()
                .anyMatch(role -> role == UserRole.ADMIN_SUPPORT || role == UserRole.ADMIN_CATALOG
                        || role == UserRole.ADMIN_SUPER || role == UserRole.DPO);
        if (hasAdminRole) {
            throw new BusinessRuleException(IdentityErrorCode.TOTP_REQUIRED_FOR_ROLE);
        }

        totpRepository.delete(userId);
        recoveryCodeRepository.deleteByUserId(userId);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("MFA_TOTP_REMOVED")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    /** B14 — trang thai TOTP + so ma khoi phuc con lai. */
    @Transactional(readOnly = true)
    public TotpStatusView status(UUID userId) {
        MfaTotp totp = totpRepository.findByUserId(userId).orElse(null);
        if (totp == null) {
            return new TotpStatusView(null, null, 0, null);
        }
        long remaining = recoveryCodeRepository.countUnusedByUserId(userId);
        return TotpStatusView.of(totp, remaining);
    }

    /** A10/A11: tai khoan co TOTP ACTIVE khong — dung de quyet dinh buoc 2 khi dang nhap. */
    @Transactional(readOnly = true)
    public boolean totpEnabled(UUID userId) {
        return totpRepository.findByUserId(userId)
                .map(MfaTotp::isActive)
                .orElse(false);
    }

    /** Ket qua {@link #verifyRecovery}. */
    public record RecoveryResult(long recoveryCodesRemaining, boolean enrollmentRequired) {
    }

    private List<String> generateRecoveryCodes(UUID userId, Instant now) {
        UUID batchId = UUID.randomUUID();
        int pepperVersion = recoveryCodeHasher.currentPepperVersion();
        List<MfaRecoveryCode> rows = new ArrayList<>(RECOVERY_CODE_BATCH_SIZE);
        List<String> rawCodes = new ArrayList<>(RECOVERY_CODE_BATCH_SIZE);
        for (int i = 0; i < RECOVERY_CODE_BATCH_SIZE; i++) {
            String raw = Base32.newRecoveryCode();
            rawCodes.add(raw);
            rows.add(new MfaRecoveryCode(
                    UUID.randomUUID(),
                    userId,
                    recoveryCodeHasher.hash(pepperVersion, raw),
                    pepperVersion,
                    batchId,
                    null,
                    null,
                    now));
        }
        recoveryCodeRepository.insertBatch(userId, rows);
        return rawCodes;
    }

    private String decryptSecret(UUID userId, MfaTotp totp) {
        byte[] plain = piiCipher.decrypt(CryptoPurpose.MFA_TOTP,
                PiiCipher.aad(TOTP_TABLE, TOTP_SECRET_COLUMN, userId),
                totp.secretEnc());
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static String otpauthUri(String email, String secretBase32, String algorithm,
                                     int digits, int periodSeconds) {
        return "otpauth://totp/CatCheck:" + email
                + "?secret=" + secretBase32
                + "&issuer=CatCheck"
                + "&algorithm=" + algorithm
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    private void requireRateLimit(RateLimitRule rule, String subject) {
        RateLimiter.RetryAfter retryAfter = rateLimiter.rejectIfLimited(rule, subject);
        if (retryAfter != null) {
            throw new RateLimitedException(IdentityErrorCode.RATE_LIMITED,
                    retryAfter.headerSeconds(), rule.name());
        }
    }

    private void auditDenied(UUID userId, String reason, AuthRequestContext context) {
        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("MFA_TOTP_VERIFY")
                .denied()
                .meta("reason", reason)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }
}
