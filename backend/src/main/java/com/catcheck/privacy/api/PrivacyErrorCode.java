package com.catcheck.privacy.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module privacy.
 *
 * <p>Tên mã và HTTP status chép nguyên văn từ p8 §8.2.4(c) "Người dùng, consent & chính
 * sách" và §8.2.4(i) "Quyền chủ thể dữ liệu (DSAR)" — Part 8 sở hữu danh mục mã lỗi.
 * {@link #code()} đồng thời là khoá tra message trong {@code messages/privacy_vi.properties}
 * / {@code privacy_en.properties}.</p>
 *
 * <p><b>Đăng ký vào {@code ErrorCodeRegistry}</b>: chỉ 14 hằng được expose làm
 * {@code @Bean ErrorCode} riêng trong {@code PrivacyErrorCodeConfiguration} — KHÔNG dùng
 * {@code @Bean List<ErrorCode>}. Bốn hằng dùng chung ({@link #VALIDATION_FAILED},
 * {@link #FORBIDDEN}, {@link #RATE_LIMITED}, {@link #CONSENT_PURPOSE_UNKNOWN}) và
 * {@link #AVATAR_INVALID} <b>không đăng ký</b>: chúng đã thuộc nhóm (a)/(b) của p8 và đã
 * được các module khác khai báo — {@code ErrorCodeRegistry.validate} ném
 * {@code IllegalStateException} khi hai enum <b>đã đăng ký</b> trả cùng {@code code()}. Hai
 * hằng vẫn nằm trong enum này để service <b>ném</b> được (throw không cần đăng ký), nhưng
 * việc nạp bean thuộc về W3 khi gộp nhóm chung lên {@code shared.error} — xem
 * {@code docs/handovers/A2.md}.</p>
 */
public enum PrivacyErrorCode implements ErrorCode {

    /* --- (c) Người dùng, consent & chính sách — đã đăng ký --- */

    /**
     * 409 — rút {@code SERVICE_CORE} (p15 §15.3.5): mục đích này tương đương yêu cầu xoá
     * tài khoản. Tham số: {@code purposeCode}, {@code alternativeAction}.
     */
    CONSENT_MANDATORY_CANNOT_WITHDRAW("consent-mandatory-cannot-withdraw", HttpStatus.CONFLICT),

    /**
     * 403 — thao tác cần một consent chưa được cấp (ví dụ bật push khi chưa đồng ý
     * {@code HEALTH_REMINDER_PUSH}). Tham số: {@code purposeCode}, {@code purposeLabel}.
     */
    CONSENT_REQUIRED("consent-required", HttpStatus.FORBIDDEN),

    /**
     * 403 — chính sách có bản mới {@code requires_reconsent = true} (p15 REQ-VER-03).
     * Tham số: {@code docCode}, {@code version}.
     */
    POLICY_REACCEPT_REQUIRED("policy-reaccept-required", HttpStatus.FORBIDDEN),

    /** 404 — ack một version không tồn tại. */
    POLICY_VERSION_NOT_FOUND("policy-version-not-found", HttpStatus.NOT_FOUND),

    /* --- (i) Quyền chủ thể dữ liệu (DSAR) — đã đăng ký --- */

    /** 404 — {@code publicRef} không tồn tại hoặc không thuộc user (p8 §8.2.5: tài nguyên của người khác trả 404). */
    DSAR_NOT_FOUND("dsar-not-found", HttpStatus.NOT_FOUND),

    /** 404 — cấu hình retention không tồn tại. */
    RETENTION_POLICY_NOT_FOUND("retention-policy-not-found", HttpStatus.NOT_FOUND),

    /**
     * 403 — chưa xác minh OTP trước khi xuất/xoá (p15 REQ-DSAR-04). Tham số:
     * {@code challengeId}, {@code email} (đã che).
     */
    DSAR_IDENTITY_VERIFICATION_REQUIRED("dsar-identity-verification-required", HttpStatus.FORBIDDEN),

    /**
     * 429 — &gt; 1 yêu cầu xuất / 24 h (p15 §15.4.5). Mã riêng, KHÔNG dùng
     * {@code RATE_LIMITED} (p8 §8.3.5). Tham số: {@code retryAfterSeconds}, {@code lastRequestedAt}.
     */
    DSAR_EXPORT_RATE_LIMITED("dsar-export-rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    /** 409 — tải khi chưa {@code COMPLETED}. Tham số: {@code status}. */
    DSAR_EXPORT_NOT_READY("dsar-export-not-ready", HttpStatus.CONFLICT),

    /** 410 — quá 72 giờ (p15 §15.4.5). Tham số: {@code expiredAt}. */
    DSAR_EXPORT_EXPIRED("dsar-export-expired", HttpStatus.GONE),

    /** 410 — link một lần đã dùng (p15 §15.4.5). Tham số: {@code downloadedAt}. */
    DSAR_EXPORT_ALREADY_DOWNLOADED("dsar-export-already-downloaded", HttpStatus.GONE),

    /** 409 — đã có {@code dsar_request(ERASE)} đang mở (p8). Tham số: {@code publicRef}, {@code scheduledAt}, {@code cancelUntil}. */
    DELETION_ALREADY_REQUESTED("deletion-already-requested", HttpStatus.CONFLICT),

    /** 409 — huỷ sau khi hết 7 ngày ân hạn. Tham số: {@code executedAt}. */
    DELETION_GRACE_EXPIRED("deletion-grace-expired", HttpStatus.CONFLICT),

    /** 409 — huỷ khi không có yêu cầu nào. */
    DELETION_NOT_REQUESTED("deletion-not-requested", HttpStatus.CONFLICT),

    /** 400 — {@code requestType} ngoài danh sách. Tham số: {@code allowed[]}. */
    DSAR_REQUEST_TYPE_UNSUPPORTED("dsar-request-type-unsupported", HttpStatus.BAD_REQUEST),

    /** 409 — bật hạn chế khi đang hạn chế. */
    RESTRICTION_ALREADY_ACTIVE("restriction-already-active", HttpStatus.CONFLICT),

    /**
     * 409 — người đề xuất xoá tài khoản tự bấm duyệt (quy tắc hai người, p15 REQ-RBAC-03 +
     * p8 L53 <i>"người đề xuất không tự duyệt"</i>). Cùng tình huống với
     * {@code CONTENT_SELF_APPROVAL_FORBIDDEN} của L44 nên dùng cùng status 409.
     *
     * <p>⚠️ Mã này <b>chưa có trong p8 §8.2.4(i)</b> — xem handoff H15.171.</p>
     */
    DSAR_SELF_APPROVAL_FORBIDDEN("dsar-self-approval-forbidden", HttpStatus.CONFLICT),

    /* --- Retention & sự cố bảo mật (nhóm L: L57–L62) --- */

    /**
     * 422 — sửa {@code retention_policy} vượt trần cứng của bất biến I15 (p4 §4.5.1):
     * {@code SCAN_IMAGE ≤ 14} ngày (quyết định owner #9) và grace xoá tài khoản
     * {@code ≤ 7} ngày (TD-05). Tên mã lấy nguyên văn ô L57 của p8 §8.4.12.
     * Tham số: {@code policyCode}, {@code maxDays}.
     */
    RETENTION_LIMIT_EXCEEDED("retention-limit-exceeded", HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * 404 — {@code security_incident.id} không tồn tại (L61, và {@code incidentId} bắt buộc
     * của L62).
     *
     * <p>⚠️ Mã này <b>chưa có trong p8 §8.2.4</b> — xem handoff H15.171.</p>
     */
    SECURITY_INCIDENT_NOT_FOUND("security-incident-not-found", HttpStatus.NOT_FOUND),

    /* --- Dùng chung — KHÔNG đăng ký (đã thuộc nhóm (a)/(b) của p8, xem javadoc class) --- */

    /** 400 — một hoặc nhiều field không hợp lệ (nhóm (a)). Chỉ ném, không nạp bean. */
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST),

    /** 403 — thiếu quyền (nhóm (b)). Chỉ ném, không nạp bean. */
    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    /** 429 — vượt rate limit (nhóm (a)). Chỉ ném, không nạp bean. */
    RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    /**
     * 400 — {@code purposeCode} không có trong {@code consent_purpose} (nhóm (c)).
     * Hằng này ĐÃ được {@code identity.api.IdentityErrorCode} đăng ký — ném qua enum này
     * cho đúng mã p8 mà không tạo trùng khi W3 gộp nhóm. Tham số: {@code purposeCode}.
     */
    CONSENT_PURPOSE_UNKNOWN("consent-purpose-unknown", HttpStatus.BAD_REQUEST),

    /** 400 — ảnh đại diện sai định dạng/kích thước (nhóm (c)). Chỉ ném, không nạp bean. */
    AVATAR_INVALID("avatar-invalid", HttpStatus.BAD_REQUEST),

    /** 400 — {@code cursor} sai định dạng hoặc đã hết hiệu lực (nhóm (a), dùng ở C14). Chỉ ném, không nạp bean. */
    PAGINATION_CURSOR_INVALID("pagination-cursor-invalid", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    PrivacyErrorCode(String slug, HttpStatus status) {
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
