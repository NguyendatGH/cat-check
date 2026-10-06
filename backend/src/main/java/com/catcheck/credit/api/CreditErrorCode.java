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
    WRITE_ACCESS_EXPIRED("write-access-expired", HttpStatus.FORBIDDEN),

    /* --- Quan tri ma kich hoat & goi — L19..L26 (p8 §8.4.12) --- */

    /** 422 — sinh quá {@code max} mã một lần (p8 §8.2.4). Tham số: {@code max}. */
    ACTIVATION_BATCH_TOO_LARGE("activation-batch-too-large", HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * 410 — CSV mã thô đã tải (L22: <i>"một lần duy nhất"</i>) hoặc không còn trong bộ nhớ.
     *
     * <p>Một mã cho CẢ HAI tình huống là cố ý: DB chỉ lưu {@code code_hash} (p5 §5.9) nên về
     * phía người dùng hai trường hợp này giống nhau tuyệt đối — mã thô không còn tồn tại ở đâu
     * và đường duy nhất là void cả lô rồi sinh lô mới (p14 §14.3.2 mục 4).</p>
     */
    ACTIVATION_CSV_ALREADY_DOWNLOADED("activation-csv-already-downloaded", HttpStatus.GONE),

    /**
     * 409 — {@code productionBatch} đã có mã phát hành trước đó.
     *
     * <p>Mã này KHÔNG có trong p8 §8.2.4 vì p8 giả định tồn tại một {@code batchId} riêng, còn
     * p4 lại không có bảng {@code activation_batch} nào để cấp id đó. Định danh lô vì vậy là
     * chính {@code production_batch}, và nó phải duy nhất nếu không thì L21/L22/L24 nói về một
     * tập mã không xác định. Xem handoff H15.98.</p>
     */
    ACTIVATION_BATCH_EXISTS("activation-batch-exists", HttpStatus.CONFLICT),

    /** 404 — không có lô nào mang định danh đó. */
    ACTIVATION_BATCH_NOT_FOUND("activation-batch-not-found", HttpStatus.NOT_FOUND),

    /** 404 — {@code package_plan.code} không tồn tại (L26, và L20 khi chọn gói đã bị xoá). */
    PACKAGE_PLAN_NOT_FOUND("package-plan-not-found", HttpStatus.NOT_FOUND),

    /* --- Dieu chinh credit thu cong — L10 (p8 §8.4.12, p14 §14.4.4) --- */

    /**
     * 422 — vượt trần an toàn {@code app_setting} (mặc định 200 credit/lần, 1 000/ngày/admin —
     * p14 §14.4.4 bước 5). Tham số: {@code maxPerOperation}, {@code maxPerDay},
     * {@code remainingToday}.
     */
    CREDIT_ADJUST_LIMIT_EXCEEDED("credit-adjust-limit-exceeded", HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * 409 — thu hồi nhiều hơn tổng {@code credit_batch.remaining_amount} còn hiệu lực
     * (p8 §8.2.4(f)). Tham số: {@code available}, {@code requested}.
     *
     * <p>Cố ý KHÔNG dùng {@link #CREDIT_INSUFFICIENT} (402): mã đó là của <b>người dùng</b> hết
     * lượt quét và p8 §8.2.3 dành riêng status 402 cho đúng nó. Ở đây không ai hết credit — một
     * thao tác admin đang đòi thu hồi số không tồn tại.</p>
     */
    CREDIT_ADJUST_EXCEEDS_BALANCE("credit-adjust-exceeds-balance", HttpStatus.CONFLICT);

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
