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

    public record OAuthResult(
            UUID userId,
            EmailAddress email,
            String fullName,
            Set<String> roles,
            boolean newlyCreated) {
    }

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
            throw new BusinessRuleException(IdentityErrorCode.OAUTH_EMAIL_UNVERIFIED);
        }

        EmailAddress emailAddress = EmailAddress.of(email);
        Instant now = clock.instant();

        var existingGoogle = identityRepository.findByProviderSubject(IdentityProvider.GOOGLE, sub);
        if (existingGoogle.isPresent()) {
            UserIdentity google = existingGoogle.get();
            UserAccount account = accountRepository.findById(google.userId())
                    .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OAUTH_FAILED));
            requireLoginAllowed(account);
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

        if (identityRepository.existsByProviderSubject(IdentityProvider.GOOGLE, sub)) {
            throw new ConflictException(IdentityErrorCode.OAUTH_ACCOUNT_LINK_CONFLICT);
        }

        var existingByEmail = accountRepository.findByEmail(emailAddress);
        if (existingByEmail.isPresent()) {
            UserAccount account = existingByEmail.get();
            requireLoginAllowed(account);
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

        UUID userId = UUID.randomUUID();
        String fullName = name != null && !name.isBlank() ? name : "User";
        accountRepository.insert(new UserAccount(
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

    private static void requireLoginAllowed(UserAccount account) {
        if (account.status() != UserStatus.ACTIVE || account.isProcessingRestricted()) {
            throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
    }
}
