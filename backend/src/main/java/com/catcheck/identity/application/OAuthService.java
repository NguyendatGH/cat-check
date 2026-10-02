package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dang nhap Google OAuth (p11 §11.4, p3 F2).
 *
 * <p><b>Backend-driven redirect</b> (p11 §11.4.1): Spring Security {@code oauth2Login()}
 * lam toan bo; chi tay vao {@link com.catcheck.identity.infrastructure.security.OAuth2UserServiceAdapter}
 * de map {@code OidcUser} sang {@code user_identity}.</p>
 *
 * <p>Quy tac linking (p11 §11.2.5):</p>
 * <ol>
 *   <li>Tim theo {@code (GOOGLE, sub)} — neu co, dung tai khoan do.</li>
 *   <li>Neu {@code email_verified = true} va email da co tai khoan → lien ket them.</li>
 *   <li>Neu email chua co → tao tai khoan {@code ACTIVE} (email da duoc Google xac
 *       minh, KHONG bat OTP — p11 §11.4.3), nhung van phai qua man hinh consent.</li>
 * </ol>
 *
 * <p><b>KHONG bao gio dung email lam provider_user_id</b> — {@code sub} la thu duy nhat
 * Google cam ket on dinh (p4 §A2).</p>
 */
@Service
public class OAuthService {

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public OAuthService(UserAccountRepository accountRepository,
                        UserIdentityRepository identityRepository,
                        UserRoleRepository roleRepository,
                        AuditLogService auditLogService,
                        Clock clock) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Ket qua xu ly user Google — controller dung de tao phien. */
    public record OAuthResult(
            UUID userId,
            EmailAddress email,
            String fullName,
            Set<String> roles,
            boolean newlyCreated) {
    }

    /**
     * Map {@code OidcUser} da xac thuc sang tai khoan CatCheck.
     *
     * @param claims claim cua Google: {@code sub}, {@code email}, {@code email_verified}, {@code name}
     * @throws ConflictException {@code sub} da lien ket tai khoan khac
     */
    @Transactional
    public OAuthResult findOrCreate(Map<String, Object> claims, AuthRequestContext context) {
        String sub = (String) claims.get("sub");
        String email = (String) claims.get("email");
        boolean emailVerified = Boolean.TRUE.equals(claims.get("email_verified"));
        String name = (String) claims.get("name");

        if (sub == null || sub.isBlank()) {
            throw new BusinessRuleException(IdentityErrorCode.OAUTH_FAILED);
        }
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException(IdentityErrorCode.OAUTH_FAILED);
        }
        if (!emailVerified) {
            // p11 §11.4.2: email_verified phai de quyet dinh linking.
            throw new BusinessRuleException(IdentityErrorCode.OAUTH_EMAIL_UNVERIFIED);
        }

        EmailAddress emailAddress = EmailAddress.of(email);
        Instant now = clock.instant();

        // L1: tim theo (GOOGLE, sub) — OAuth callback lan sau cua cung mot tai khoan.
        var existingGoogle = identityRepository.findByProviderSubject(IdentityProvider.GOOGLE, sub);
        if (existingGoogle.isPresent()) {
            UserIdentity google = existingGoogle.get();
            UserAccount account = accountRepository.findById(google.userId())
                    .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OAUTH_FAILED));
            identityRepository.touchLastUsed(google.id(), now);
            auditLogService.record(AuditEvent.builder()
                    .subjectUser(account.id())
                    .action("AUTH_LOGIN")
                    .meta("provider", "GOOGLE")
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .userAgent(context.userAgent())
                    .build());
            return result(account, false);
        }

        // L2: sub da lien ket tai khoan khac — khong the ghi de.
        if (identityRepository.existsByProviderSubject(IdentityProvider.GOOGLE, sub)) {
            throw new ConflictException(IdentityErrorCode.OAUTH_ACCOUNT_LINK_CONFLICT);
        }

        // L3: email da co tai khoan — lien ket them Google.
        var existingByEmail = accountRepository.findByEmail(emailAddress);
        if (existingByEmail.isPresent()) {
            UserAccount account = existingByEmail.get();
            identityRepository.insert(new UserIdentity(
                    UUID.randomUUID(), account.id(), IdentityProvider.GOOGLE, sub,
                    emailAddress.value(), true, null, now, now, now, now));
            auditLogService.record(AuditEvent.builder()
                    .subjectUser(account.id())
                    .action("AUTH_LOGIN")
                    .meta("provider", "GOOGLE")
                    .meta("linked", true)
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .userAgent(context.userAgent())
                    .build());
            return result(account, false);
        }

        // L4: user moi — email da duoc Google xac minh nen KHONG bat OTP (p11 §11.4.3).
        UUID userId = UUID.randomUUID();
        String fullName = name != null && !name.isBlank() ? name : "User";
        accountRepository.insert(new UserAccount(
                // "timezone" NOT NULL — cung bug/cung fix voi RegistrationService.register(),
                // xem chu thich o do.
                userId, emailAddress, now, fullName, null, null, null, null,
                com.catcheck.identity.domain.AppLocale.VI, "Asia/Ho_Chi_Minh",
                UserStatus.ACTIVE, OnboardingStatus.ACCOUNT_ONLY,
                0, null, null, null, null, null, null, null, null, null, null,
                now, now));
        identityRepository.insert(new UserIdentity(
                UUID.randomUUID(), userId, IdentityProvider.LOCAL, emailAddress.value(),
                emailAddress.value(), true, null, now, null, now, now));
        identityRepository.insert(new UserIdentity(
                UUID.randomUUID(), userId, IdentityProvider.GOOGLE, sub,
                emailAddress.value(), true, null, now, now, now, now));
        roleRepository.grant(userId, UserRole.USER, null);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_REGISTER")
                .meta("provider", "GOOGLE")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return result(accountRepository.findById(userId).orElseThrow(), true);
    }

    private OAuthResult result(UserAccount account, boolean newlyCreated) {
        var roles = roleRepository.findByUserId(account.id()).stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new OAuthResult(account.id(), account.email(), account.fullName(), roles, newlyCreated);
    }
}
