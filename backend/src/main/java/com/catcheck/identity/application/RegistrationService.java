package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.privacy.spi.RegistrationConsentEvent;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.PasswordPolicy;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dang ky tai khoan (p3 F1, p8 A3 + A4 + A5, p11 §11.1.1).
 *
 * <p><b>Da hoan gop ba endpoint p8 vao mot luong</b> vi p8 va p11 mo ta hai hinh thuc
 * khac nhau cho cung mot viec:</p>
 * <ul>
 *   <li>p8 A3 {@code POST /auth/register} tra {@code 202}, A4/A5 la cac buoc rieng;</li>
 *   <li>p11 §11.1.1 va p8 A3 cung cho phep gui thang {@code otp_ticket} da co — tuc la
 *       client da xac minh o mot thiet bi khac, khong can lai.</li>
 * </ul>
 * <p>Hop nhat: {@code otpTicket} <b>optional</b>. Khong co =&gt; tao tai khoan
 * {@code PENDING_VERIFICATION} + gui OTP ngay ({@code 202}). Co =&gt; tie ticket, kich
 * hoat tai khoan, tra session ({@code 201}). Client theo p8 nguyen van chay duoc, client
 * theo p11 cung duoc.</p>
 */
@Service
public class RegistrationService {

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final OtpService otpService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegistrationService(UserAccountRepository accountRepository,
                               UserIdentityRepository identityRepository,
                               UserRoleRepository roleRepository,
                               PasswordHasher passwordHasher,
                               PasswordPolicy passwordPolicy,
                               OtpService otpService,
                               AuditLogService auditLogService,
                               ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.otpService = otpService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** {@code authenticated = true} nghia la client co the vao thang, khong can OTP nua. */
    public record RegistrationResult(
            UUID userId,
            EmailAddress email,
            String fullName,
            boolean emailVerified,
            boolean authenticated) {
    }

    /**
     * @param rawOtpTicket {@code null} neu client chua xac minh o thiet bi nao
     * @param locale       {@code null} =&gt; {@link AppLocale#VI}
     * @param consents     lua chon consent tho user tick luc dang ky (p15 §15.3.3); ro hoac
     *                     {@code null} khi request khong kem consent nao (vd lan goi lai kem
     *                     {@code otpTicket} de kich hoat) — khong phat {@link RegistrationConsentEvent}
     *                     trong truong hop do
     */
    @Transactional
    public RegistrationResult register(String rawEmail,
                                       String rawPassword,
                                       String fullName,
                                       String locale,
                                       String rawOtpTicket,
                                       List<RegistrationConsentEvent.ConsentGrant> consents,
                                       AuthRequestContext context) {
        EmailAddress email = EmailAddress.of(rawEmail);
        Instant now = clock.instant();

        // Bug that da sua: nhanh co otpTicket PHAI kiem truoc existsByEmail — tai khoan
        // PENDING_VERIFICATION do chinh loi goi /register (khong ticket) tao ra o buoc truoc
        // CHAC CHAN da ton tai luc client quay lai voi ticket de kich hoat, nen kiem
        // existsByEmail truoc se luon nem 409 va lam nhanh "kich hoat bang ticket"
        // (completePreverifiedRegistration) khong bao gio toi duoc (xac nhan that bang cach
        // chay dung luong dang ky local: register -> verify otp -> register lai kem ticket ->
        // 409 EMAIL_ALREADY_REGISTERED thay vi kich hoat). completePreverifiedRegistration tu no
        // da kiem emailVerifiedAt/email khop truoc khi kich hoat nen khong mat an toan.
        if (rawOtpTicket != null && !rawOtpTicket.isBlank()) {
            return completePreverifiedRegistration(email, rawPassword, locale, rawOtpTicket, now, context);
        }

        if (accountRepository.existsByEmail(email)) {
            // 409 chu khong phai 403: client da gui mat khau nen da chung minh so huu hoi
            // thu. Thong bao "da duoc dang ky" khong lo gi so voi nguoi la, va van dung
            // duong "quen mat khau" cho chinh nguoi so huu.
            auditLogService.record(AuditEvent.builder()
                    .action("AUTH_REGISTER")
                    .denied()
                    .meta("reason", "email_already_registered")
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .build());
            throw new ConflictException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        requireAcceptablePassword(rawPassword);

        UUID userId = UUID.randomUUID();
        accountRepository.insert(new UserAccount(
                // "timezone" NOT NULL DEFAULT 'Asia/Ho_Chi_Minh' o V5 — DEFAULT chi ap dung khi
                // INSERT bo qua cot, khong ap dung khi truyen thang null. Bug that: truoc day
                // truyen null lam INSERT vi pham NOT NULL (xac nhan qua PSQLException that khi
                // test dang ky local). Cung gia tri mac dinh dang dung o
                // export.application.ExportRequestService.
                userId, email, null, fullName, null, null, null, null,
                parseLocale(locale), "Asia/Ho_Chi_Minh",
                UserStatus.PENDING_VERIFICATION, OnboardingStatus.ACCOUNT_ONLY,
                0, null, null, null, null, null, null, null, null, null, null,
                now, now));
        // "provider_user_id" NOT NULL — bug that: truyen null lam INSERT vi pham NOT NULL
        // (xac nhan qua PSQLException that khi test dang ky local). OAuthService da lam dung:
        // provider LOCAL dung chinh email lam provider_user_id, khong co ID ngoai nao khac.
        identityRepository.insert(new UserIdentity(
                UUID.randomUUID(), userId, IdentityProvider.LOCAL, email.value(), email.value(),
                false, passwordHasher.hash(rawPassword), now, null, now, now));
        roleRepository.grant(userId, UserRole.USER, null);

        // Phat RegistrationConsentEvent CUNG transaction voi viec tao tai khoan (tien le
        // scan.api.ScanSavedEvent) de module privacy ghi consent_record — xem javadoc
        // RegistrationConsentEvent de privacy ghi bang chung consent mot cach nguyen tu.
        // Rong/null khi request dang ky khong kem consent (vd goi lai kem otpTicket de kich
        // hoat) — khong phat su kien de tranh ConsentService.recordConsents nem loi tren danh
        // sach rong.
        if (consents != null && !consents.isEmpty()) {
            // parseLocale (khong phai chuoi tho tu client) — cung gia tri da dung de ghi
            // app_user.locale ben tren, tranh mot locale la lam ConsentService khong tim
            // thay policy_version dang hieu luc roi lam rollback ca dang ky.
            eventPublisher.publishEvent(new RegistrationConsentEvent(
                    userId, consents, parseLocale(locale).code(), context.ipAddress(),
                    context.userAgent(), context.requestId(), now));
        }

        // Gui OTP ngay trong request dang ky: p8 A3 tra 202 va FE chuyen sang man
        // "xac thuc OTP" — tach them mot vong goi lai chi lam nguoi dung cho.
        otpService.requestOtp(email.value(), OtpPurpose.REGISTER_VERIFY, userId, context);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_REGISTER")
                .meta("onboarding", OnboardingStatus.ACCOUNT_ONLY.name())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new RegistrationResult(userId, email, fullName, false, false);
    }

    /**
     * Client da co ticket {@code REGISTER_VERIFY} (verify o thiet bi khac): tie ticket,
     * kich hoat tai khoan, tra session. Khong tao tai khoan moi.
     */
    private RegistrationResult completePreverifiedRegistration(EmailAddress email,
                                                              String rawPassword,
                                                              String locale,
                                                              String rawOtpTicket,
                                                              Instant now,
                                                              AuthRequestContext context) {
        EmailOtpChallenge challenge = otpService.consumeTicket(rawOtpTicket, OtpPurpose.REGISTER_VERIFY);
        UserAccount account = accountRepository.findById(challenge.userId())
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));

        if (account.emailVerifiedAt() != null) {
            throw new ConflictException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        if (!account.email().equals(email)) {
            // Ticket cua email nao thi chi kich hoat dung email do.
            throw new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID);
        }
        requireAcceptablePassword(rawPassword);

        accountRepository.markEmailVerified(account.id(), now);
        identityRepository.findLocal(account.id())
                .ifPresent(identity -> identityRepository.touchLastUsed(identity.id(), now));

        auditLogService.record(AuditEvent.builder()
                .subjectUser(account.id())
                .action("AUTH_EMAIL_VERIFIED")
                .meta("via", "register_ticket")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());

        return new RegistrationResult(account.id(), account.email(), account.fullName(), true, true);
    }

    /**
     * Kich hoat tai khoan sau khi verify OTP thanh cong (p8 A5 co {@code register=true}).
     * Tach rieng khoi {@link #register} vi A5 phai chay cho <b>moi</b> tai khoan dang
     * cho xac minh, khong chi tai khoan vua tao trong request nay.
     */
    @Transactional
    public RegistrationResult activateWithTicket(String rawOtpTicket, AuthRequestContext context) {
        EmailOtpChallenge challenge = otpService.consumeTicket(rawOtpTicket, OtpPurpose.REGISTER_VERIFY);
        Instant now = clock.instant();
        UserAccount account = accountRepository.findById(challenge.userId())
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));

        if (account.emailVerifiedAt() == null) {
            accountRepository.markEmailVerified(account.id(), now);
        }

        auditLogService.record(AuditEvent.builder()
                .subjectUser(account.id())
                .action("AUTH_EMAIL_VERIFIED")
                .meta("via", "otp_verify")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());

        return new RegistrationResult(account.id(), account.email(), account.fullName(), true, true);
    }

    /**
     * Do dai sai =&gt; {@code PASSWORD_TOO_WEAK} (p8 §8.2.4 gop ca {@code < 8} va {@code > 72}
     * vao mot ma). Repeated/sequential/breached deu la "qua de doan" =&gt; mot ma, de khong
     * cho nguoi dung doan xem luat nao trong danh sach da chan.
     */
    private void requireAcceptablePassword(String rawPassword) {
        PasswordPolicy.Violation violation = passwordPolicy.check(rawPassword);
        if (violation == null) {
            return;
        }
        throw switch (violation) {
            case TOO_SHORT, TOO_LONG -> new BusinessRuleException(IdentityErrorCode.PASSWORD_TOO_WEAK);
            case REPEATED_CHARS, SEQUENTIAL_CHARS, BLOCKED ->
                    new BusinessRuleException(IdentityErrorCode.PASSWORD_BREACHED);
        };
    }

    private static AppLocale parseLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return AppLocale.VI;
        }
        try {
            return AppLocale.fromCode(locale);
        } catch (IllegalArgumentException ex) {
            return AppLocale.VI;
        }
    }
}
