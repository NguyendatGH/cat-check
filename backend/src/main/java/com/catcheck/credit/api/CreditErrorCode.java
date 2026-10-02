package com.catcheck.credit.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module credit.
 *
 * <p>Tên mã và HTTP status chốt theo p8 §8.2.4 — Part 8 sở hữu danh mục mã lỗi, nên ở đây
 * chép NGUYÊN VĂN, không tự đặt lại tên. {@link #code()} đồng thời là khoá tra message trong
 * {@code messages/credit_vi.properties} / {@code credit_en.properties}.</p>
 *
 * <p>Đã đăng ký vào {@code ErrorCodeRegistry} — mỗi hằng là một {@code @Bean ErrorCode} riêng
 * trong {@code CreditErrorCodeConfiguration}. <b>Không</b> dùng {@code @Bean List<ErrorCode>}:
 * registry nhận {@code List} qua constructor, một bean {@code List<ErrorCode>} không bao giờ được
 * gom vào và sẽ làm mất tính định danh duy nhất của constructor.</p>
 */
public enum CreditErrorCode implements ErrorCode {

    /**
     * 402 — hết credit khả dụng <b>và</b> hết lượt trial tại thời điểm lưu (p8 §8.2.4(e)).
     * 402 là status dành riêng cho mã này và không dùng cho mã khác (p8 §8.2.3).
     */
    CREDIT_INSUFFICIENT("credit-insufficient", HttpStatus.PAYMENT_REQUIRED),

    /**
     * 400 — sai định dạng hoặc sai ký tự checksum, bắt được TRƯỚC khi tra DB (p5 §5.9).
     * Tham số: {@code expectedFormat}.
     */
    ACTIVATION_CODE_MALFORMED("activation-code-malformed", HttpStatus.BAD_REQUEST),

    /**
     * 422 — mã không tồn tại hoặc {@code status = VOID} (p8 §8.2.4(f)).
     *
     * <p>p11 §11.7.4: KHÔNG bao giờ phân biệt "không tồn tại" với "đã dùng" cho người dùng
     * thường — cả hai trả cùng một mã, chỉ admin tra cứu mới thấy trạng thái thật.</p>
     */
    ACTIVATION_CODE_INVALID("activation-code-invalid", HttpStatus.UNPROCESSABLE_ENTITY),

    /** 409 — {@code status = REDEEMED} (bất biến I24). Tham số: {@code redeemedAt}, {@code byCurrentUser}. */
    ACTIVATION_CODE_ALREADY_USED("activation-code-already-used", HttpStatus.CONFLICT),

    /** 410 — {@code valid_until < now}; đây là hạn KÍCH HOẠT, khác hạn credit (p5 §5.5). Tham số: {@code validUntil}. */
    ACTIVATION_CODE_EXPIRED("activation-code-expired", HttpStatus.GONE),

    /** 403 — tính năng không có trong {@code user_entitlement.features} (p5 R5). Tham số: {@code feature}, {@code currentPackage}, {@code requiredPackage}. */
    FEATURE_NOT_IN_PLAN("feature-not-in-plan", HttpStatus.FORBIDDEN),

    /** 403 — quyền tạo mới (scan mới, tạo hồ sơ mèo vượt hạn mức) đã hết hạn cùng credit (p5 R5). */
    WRITE_ACCESS_EXPIRED("write-access-expired", HttpStatus.FORBIDDEN);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    CreditErrorCode(String slug, HttpStatus status) {
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
