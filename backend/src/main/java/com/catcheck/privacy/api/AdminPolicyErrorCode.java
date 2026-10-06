package com.catcheck.privacy.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi của L46–L48 — quản trị {@code policy_version} (p8 §8.4.12 mục (c)).
 *
 * <p><b>Vì sao một enum riêng thay vì thêm hằng vào {@link PrivacyErrorCode}:</b> file đó đang
 * được gói DSAR/retention/sự cố sửa song song; tách ra để hai đợt không tranh nhau cùng một
 * file. Cùng lý do mà {@code colorchart.api.ColorChartErrorCode} tồn tại riêng.</p>
 *
 * <p><b>Không đăng ký bean {@code ErrorCode}:</b> {@code ErrorCodeRegistry} nhận
 * {@code List<ErrorCode>} để chống trùng {@code code()} lúc khởi động, và
 * {@code POLICY_VERSION_NOT_FOUND} đã được {@link PrivacyErrorCode} đăng ký — đăng ký thêm ở
 * đây sẽ làm app không khởi động được. Không đăng ký KHÔNG đổi hành vi:
 * {@code GlobalExceptionHandler} đọc {@code ErrorCode} trực tiếp từ exception.</p>
 */
public enum AdminPolicyErrorCode implements ErrorCode {

    /** 404 — {@code policy_version.id} không tồn tại. Tham số: {@code id}. */
    POLICY_VERSION_NOT_FOUND("policy-version-not-found", HttpStatus.NOT_FOUND),

    /**
     * 409 — {@code (policy_type, version, locale)} đã tồn tại
     * ({@code uq_policy_version}). Sửa nội dung = tạo version mới, không bao giờ ghi đè
     * (p4 B1, p15 REQ-VER-07). Tham số: {@code policyType}, {@code version}, {@code locale}.
     */
    POLICY_VERSION_EXISTS("policy-version-exists", HttpStatus.CONFLICT),

    /**
     * 409 — publish một phiên bản đã publish. Nhận ra bằng {@code published_by IS NOT NULL};
     * bước UPDATE của L48 cũng mang chính điều kiện đó nên hai request đồng thời chỉ một thắng.
     * Tham số: {@code id}.
     */
    POLICY_VERSION_ALREADY_PUBLISHED("policy-version-already-published", HttpStatus.CONFLICT),

    /**
     * 422 — cú pháp đúng, ràng buộc nghiệp vụ sai: {@code version} không phải semver,
     * {@code effectiveFrom} không ở tương lai (bản nháp sẽ có hiệu lực ngay, bỏ qua bước
     * publish của DPO), hoặc {@code MAJOR} bump mà thiếu {@code affectedPurposes}
     * (p15 REQ-VER-02). Tham số: tên trường sai.
     */
    POLICY_VERSION_INVALID("policy-version-invalid", HttpStatus.UNPROCESSABLE_ENTITY);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    AdminPolicyErrorCode(String slug, HttpStatus status) {
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
