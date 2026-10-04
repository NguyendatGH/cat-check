package com.catcheck.identity.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Ma loi nghiep vu cua module identity.
 *
 * <p>Ten ma va HTTP status <b>chep nguyen van</b> tu bang danh muc p8 §8.2.4 — Part 8 so
 * huu danh muc ma loi nen o day khong tu dat lai ten. {@link #code()} dong thoi la khoa tra
 * message trong {@code messages/identity_vi.properties} / {@code identity_en.properties}
 * (cung kieu voi {@code CreditErrorCode}).</p>
 *
 * <p>Luu y {@code UNAUTHENTICATED} la <b>ten ma</b> trong danh muc p8, con
 * {@link HttpStatus} cua Spring Web 7 ten hang la {@code UNAUTHORIZED} — hai thu khac nhau.</p>
 *
 * <p><b>Nhom "khong lo gi"</b>: p11 §11.1.5 + p8 §8.2.4 — {@link #INVALID_CREDENTIALS} la
 * <b>mot</b> ma duy nhat cho ca ba tinh huong sai email / sai mat khau / tai khoan bi khoa;
 * {@link #PASSWORD_RESET_REQUESTED} luon 200 ke ca khi email khong ton tai.</p>
 */
public enum IdentityErrorCode implements ErrorCode {

    /* --- (c) Xac thuc / tai khoan (p8 §8.2.4) --- */

    /**
     * 401 — sai email <b>hoac</b> sai mat khau <b>hoac</b> tai khoan bi khoa. Mot thong
     * bao duy nhat, va ca hai nhanh phai ton tai cung thoi gian phan hoi (chay BCrypt
     * tren hash gia) de khong lo doi kiem chung tai khoan ton tai.
     */
    INVALID_CREDENTIALS("invalid-credentials", HttpStatus.UNAUTHORIZED),

    /** 401 — chua dang nhap hoac phien khong hop le. */
    UNAUTHENTICATED("unauthenticated", HttpStatus.UNAUTHORIZED),

    /** 401 — phien het han do idle. */
    SESSION_EXPIRED("session-expired", HttpStatus.UNAUTHORIZED),

    /** 401 — vuot absolute lifetime 90 ngay (p11 §11.1.6). */
    SESSION_ABSOLUTE_EXPIRED("session-absolute-expired", HttpStatus.UNAUTHORIZED),

    /** 404 — thu hoi mot phien khong ton tai. */
    SESSION_NOT_FOUND("session-not-found", HttpStatus.NOT_FOUND),

    /** 403 — thao tac nhay cam can xac thuc lai. Client goi {@code POST /auth/reauth} roi goi lai. */
    REAUTH_REQUIRED("reauth-required", HttpStatus.FORBIDDEN),

    /** 401 — sai mat khau/OTP/TOTP o chinh buoc step-up. */
    REAUTH_FAILED("reauth-failed", HttpStatus.UNAUTHORIZED),

    /** 409 — yeu cau {@code PASSWORD} nhung tai khoan chi co identity Google. */
    REAUTH_METHOD_UNAVAILABLE("reauth-method-unavailable", HttpStatus.CONFLICT),

    /** 401 — verify {@code id_token} that bai. */
    OAUTH_FAILED("oauth-failed", HttpStatus.UNAUTHORIZED),

    /** 403 — Google tra {@code email_verified = false}. */
    OAUTH_EMAIL_UNVERIFIED("oauth-email-unverified", HttpStatus.FORBIDDEN),

    /** 409 — {@code sub} Google da lien ket tai khoan khac. */
    OAUTH_ACCOUNT_LINK_CONFLICT("oauth-account-link-conflict", HttpStatus.CONFLICT),

    /* --- (d) Dang ky / trang thai tai khoan (p8 §8.2.4) --- */

    /** 409 — email da co tai khoan <b>da verify</b>. */
    EMAIL_ALREADY_REGISTERED("email-already-registered", HttpStatus.CONFLICT),

    /**
     * 403 — tai khoan chua xac minh email. Chi tra khi client <b>da</b> cho biet email
     * (sau khi nhap sai mat khau) — {@code POST /auth/login} van tra
     * {@link #INVALID_CREDENTIALS} de khong lo tinh huong "email co ton tai".
     */
    ACCOUNT_NOT_VERIFIED("account-not-verified", HttpStatus.FORBIDDEN),

    /** 423 — {@code status = LOCKED} do sai mat khau qua nguong p11 §11.7.2. */
    ACCOUNT_LOCKED("account-locked", HttpStatus.LOCKED),

    /** 403 — {@code processing_restricted_at} da dat (khong phai hanh dong nguoi dung). */
    ACCOUNT_RESTRICTED("account-restricted", HttpStatus.FORBIDDEN),

    /** 403 — {@code status = PENDING_VERIFICATION}: chua xac minh email (p8 §8.2.4(b)). */
    EMAIL_NOT_VERIFIED("email-not-verified", HttpStatus.FORBIDDEN),

    /** 403 — {@code status = LOCKED} (admin khoa), phat hien sau khi da co phien. */
    ACCOUNT_DISABLED("account-disabled", HttpStatus.FORBIDDEN),

    /** 403 — dang trong 7 ngay an han xoa (p15 §15.4.6). */
    ACCOUNT_DELETION_PENDING("account-deletion-pending", HttpStatus.FORBIDDEN),

    /** 403 — thieu/sai {@code X-XSRF-TOKEN} (p8 §8.2.4(b)). */
    CSRF_TOKEN_INVALID("csrf-token-invalid", HttpStatus.FORBIDDEN),

    /** 403 — thieu role (ma chung, p8 §8.2.4(b)). */
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    /** 404 — tai nguyen khong ton tai (ma chung khi khong co ma rieng, p8 §8.2.4(a)). */
    NOT_FOUND("not-found", HttpStatus.NOT_FOUND),

    /* --- (e) OTP (p8 §8.2.4) --- */

    /** 400 — ma OTP sai. Tham so: {@code attemptsLeft}. */
    OTP_INVALID("otp-invalid", HttpStatus.BAD_REQUEST),

    /** 410 — qua 5 phút. */
    OTP_EXPIRED("otp-expired", HttpStatus.GONE),

    /** 429 — sai >= 5 lan, challenge bi huy. Tham so: {@code retryAfterSeconds}. */
    OTP_LOCKED("otp-locked", HttpStatus.TOO_MANY_REQUESTS),

    /** 429 — gui lai khi chua het cooldown 60 giay. Tham so: {@code retryAfterSeconds}. */
    OTP_RESEND_COOLDOWN("otp-resend-cooldown", HttpStatus.TOO_MANY_REQUESTS),

    /** 503 — mailer loi. */
    OTP_DELIVERY_FAILED("otp-delivery-failed", HttpStatus.SERVICE_UNAVAILABLE),

    /** 400 — token dat lai sai/da dung (p8 §8.2.4(b)). */
    RESET_TOKEN_INVALID("reset-token-invalid", HttpStatus.BAD_REQUEST),

    /** 410 — qua 30 phut (p8 §8.2.4(b)). */
    RESET_TOKEN_EXPIRED("reset-token-expired", HttpStatus.GONE),

    /**
     * 400 — {@code otp_ticket} khong hop le / het han / da dung. <b>Khong</b> dung ma
     * tren: ticket la bang chung da xac minh, ma da cong bo trong email.
     */
    OTP_TICKET_INVALID("otp-ticket-invalid", HttpStatus.BAD_REQUEST),

    /* --- (f) Mat khau (p8 §8.2.4) --- */

    /** 400 — &lt; 8 hoac &gt; 72 ky tu. Tham so: {@code minLength}, {@code maxLength}. */
    PASSWORD_TOO_WEAK("password-too-weak", HttpStatus.BAD_REQUEST),

    /** 400 — nam trong danh sach mat khau pho bien. */
    PASSWORD_BREACHED("password-breached", HttpStatus.BAD_REQUEST),

    /** 409 — trung mat khau hien tai khi doi. */
    PASSWORD_REUSED("password-reused", HttpStatus.CONFLICT),

    /* --- (g) Tai khoan / ho so (p8 §8.2.4) --- */

    /** 409 — da het so phien dong thoi toi da. Tham so: {@code maxSessions}. */
    SESSION_LIMIT_REACHED("session-limit-reached", HttpStatus.CONFLICT),

    /** 422 — sua field chi doc qua {@code PATCH} (vi du {@code email}). */
    PROFILE_FIELD_NOT_EDITABLE("profile-field-not-editable", HttpStatus.UNPROCESSABLE_ENTITY),

    /** 409 — doi sang chinh email hien tai. */
    EMAIL_CHANGE_SAME_ADDRESS("email-change-same-address", HttpStatus.CONFLICT),

    /** 409 — dang co yeu cau doi email chua hoan tat. */
    EMAIL_CHANGE_PENDING("email-change-pending", HttpStatus.CONFLICT),

    /** 409 — email da thuoc tai khoan khac. */
    EMAIL_ALREADY_USED("email-already-used", HttpStatus.CONFLICT),

    /* --- (b) TOTP / MFA (p8 §8.2.4(b), p11 §11.12) --- */

    /** 403 — tai khoan admin chua qua buoc TOTP (p11 S11). */
    TOTP_REQUIRED("totp-required", HttpStatus.FORBIDDEN),

    /** 401 — ma TOTP sai. Tham so: {@code attemptsLeft}. */
    TOTP_INVALID("totp-invalid", HttpStatus.UNAUTHORIZED),

    /** 409 — bat lai khi da bat. */
    TOTP_ALREADY_ENABLED("totp-already-enabled", HttpStatus.CONFLICT),

    /** 403 — role admin nhung chua thiet lap TOTP. FE dieu huong cung sang /admin/setup-2fa. */
    TOTP_SETUP_REQUIRED("totp-setup-required", HttpStatus.FORBIDDEN),

    /**
     * 403 — goi {@code /api/v1/admin/**} khi phien chua qua buoc TOTP (p8 §8.2.4, p11 S11).
     *
     * <p>Khac {@link #TOTP_SETUP_REQUIRED}: o day tai khoan DA dang ky TOTP, chi la phien hien
     * tai chua nhap ma 6 so. FE xu ly khac nhau — {@code TOTP_SETUP_REQUIRED} dieu huong sang
     * {@code /admin/setup-2fa}, con ma nay chi can mo hop nhap ma.</p>
     */
    ADMIN_TOTP_REQUIRED("admin-totp-required", HttpStatus.FORBIDDEN),

    /**
     * 409 — admin tự khoá tài khoản của mình hoặc tự gỡ vai trò của mình (p8 §8.2.4, L14).
     *
     * <p>Chặn ở server chứ không chỉ ẩn nút: tự khoá là cách nhanh nhất để mất hoàn toàn khu
     * vực quản trị, và khôi phục thì phải can thiệp trực tiếp vào DB.</p>
     */
    ADMIN_CANNOT_MODIFY_SELF("admin-cannot-modify-self", HttpStatus.CONFLICT),

    /** 409 — role nhạy cảm chỉ cấp qua seed/công cụ vận hành. */
    ROLE_NOT_ASSIGNABLE("role-not-assignable", HttpStatus.CONFLICT),

    /** 409 — tài khoản đã có yêu cầu reset TOTP đang chờ. */
    MFA_RESET_PENDING("mfa-reset-pending", HttpStatus.CONFLICT),

    /** 409 — yêu cầu reset không còn ở trạng thái PENDING hoặc đã hết hạn. */
    MFA_RESET_NOT_PENDING("mfa-reset-not-pending", HttpStatus.CONFLICT),

    /** 409 — request và approve phải do hai admin khác nhau thực hiện. */
    MFA_RESET_TWO_PERSON_REQUIRED("mfa-reset-two-person-required", HttpStatus.CONFLICT),

    /** 409 — giới hạn ba yêu cầu reset trong 24 giờ. */
    MFA_RESET_RATE_LIMITED("mfa-reset-rate-limited", HttpStatus.CONFLICT),

    /** 429 — sai ma TOTP qua nguong p11 §11.12.3 (5 lan / 5 phut) — buoc MFA bi khoa 15 phut. */
    TOTP_LOCKED("totp-locked", HttpStatus.TOO_MANY_REQUESTS),

    /** 401 — ma khoi phuc sai HOAC da dung — mot thong bao duy nhat cho ca hai. */
    TOTP_RECOVERY_INVALID("totp-recovery-invalid", HttpStatus.UNAUTHORIZED),

    /** 409 — da dung het 10 ma khoi phuc — chi con duong reset hai nguoi. */
    TOTP_RECOVERY_EXHAUSTED("totp-recovery-exhausted", HttpStatus.CONFLICT),

    /** 409 — goi confirm/regenerate/DELETE khi chua co ban ghi {@code ACTIVE}. */
    TOTP_NOT_ENABLED("totp-not-enabled", HttpStatus.CONFLICT),

    /** 410 — ban ghi {@code PENDING} qua 10 phut da bi job don. */
    TOTP_ENROLLMENT_EXPIRED("totp-enrollment-expired", HttpStatus.GONE),

    /** 409 — tu go TOTP khi tai khoan van con role admin (p11 §11.12.3). */
    TOTP_REQUIRED_FOR_ROLE("totp-required-for-role", HttpStatus.CONFLICT),

    /* --- (b) Identity / profile (p8 §8.2.4(b,c)) --- */

    /** 409 — go identity cuoi cung (p8 B10). */
    IDENTITY_LAST_REMAINING("identity-last-remaining", HttpStatus.CONFLICT),

    /** 404 — identity khong ton tai (p8 B10). */
    IDENTITY_NOT_FOUND("identity-not-found", HttpStatus.NOT_FOUND),

    /** 400 — khong phai IANA zone id (p8 §8.2.4(g) TIMEZONE_INVALID). */
    TIMEZONE_INVALID("timezone-invalid", HttpStatus.BAD_REQUEST),

    /** 400 — anh dai dien sai dinh dang/kich thuoc (p8 §8.2.4(c) AVATAR_INVALID). */
    AVATAR_INVALID("avatar-invalid", HttpStatus.BAD_REQUEST),

    /** 400 — {@code purposeCode} khong co trong {@code consent_purpose} (p8 §8.2.4(c)). */
    CONSENT_PURPOSE_UNKNOWN("consent-purpose-unknown", HttpStatus.BAD_REQUEST),

    /* --- Chung --- */

    /**
     * 400 — mot hoac nhieu field khong hop le (p8 §8.2.4 bang "Chung"). Tham so {0} la ten
     * field sai (vi du {@code types.healthFlag} o B12).
     *
     * <p><b>KHONG dang ky bean trong {@code IdentityErrorCodeConfiguration}</b>: hang nay
     * trung {@code code()} voi {@code CatErrorCode}/{@code PrivacyErrorCode}/
     * {@code ColorChartErrorCode}... va {@code shared.error.ErrorCodeRegistry} nem
     * {@code IllegalStateException} (app khong khoi dong) khi hai enum khac nhau cung tra mot
     * {@code code()}. Cung cach xu ly ma {@code cat}/{@code privacy} dang dung cho chinh hang
     * nay — nem van chay binh thuong, chi la khong co trong registry chong trung.</p>
     */
    VALIDATION_FAILED("common/validation-failed", HttpStatus.BAD_REQUEST),

    /** 429 — vuot han muc (p8 §8.2.4(a)). Tham so: {@code retryAfterSeconds}, {@code scope}. */
    RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    /* --- Thanh cong co chu dung (khong loi) --- */

    /**
     * 200 — {@code POST /auth/password-reset/request} <b>luon</b> tra ma nay, ke ca khi
     * email khong ton tai (p3 F3, p11 §11.1.5). Khong phai loi, nhung phai la enum vi
     * client doi chieu theo {@code code}.
     */
    PASSWORD_RESET_REQUESTED("password-reset-requested", HttpStatus.OK);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    IdentityErrorCode(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public URI typeUri() {
        return URI.create(PROBLEM_BASE + slug);
    }
}
