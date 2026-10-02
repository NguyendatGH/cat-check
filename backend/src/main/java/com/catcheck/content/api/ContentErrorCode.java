package com.catcheck.content.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module content.
 *
 * <p>Tên mã và HTTP status chốt theo p8 §8.2.4(j) và bảng endpoint §8.4.12(d) — Part 8 sở hữu danh
 * mục mã lỗi, nên ở đây chép NGUYÊN VĂN, không tự đặt lại tên. {@link #code()} đồng thời là khoá
 * tra message trong {@code messages/content_vi.properties} / {@code content_en.properties} (A3 chỉ được
 * sở hữu hai file message này — xem {@code docs/handovers/A3.md}).</p>
 *
 * <p>Chưa đăng ký vào {@code ErrorCodeRegistry}: registry nhận {@code List<ErrorCode>} qua
 * constructor, nên nếu mỗi module cùng expose một {@code @Bean List<ErrorCode>} thì Spring sẽ
 * không còn bean định danh duy nhất và context không khởi động được. Cần W3 gộp lại.</p>
 */
public enum ContentErrorCode implements ErrorCode {

    /** 404 — bài không tồn tại, hoặc tồn tại nhưng chưa {@code PUBLISHED} (p8 §8.2.4(j)). */
    CONTENT_NOT_FOUND("content-not-found", HttpStatus.NOT_FOUND),

    /** 422 — publish bài có {@code claimType} khác {@code NONE} mà {@code sourceReference} rỗng (L44, bất biến I31). */
    CONTENT_SOURCE_REQUIRED("content-source-required", HttpStatus.UNPROCESSABLE_ENTITY),

    /** 409 — người soạn tự duyệt bài của chính mình (L44, p14 §14.3.5). Tham số: {@code authorId}. */
    CONTENT_SELF_APPROVAL_FORBIDDEN("content-self-approval-forbidden", HttpStatus.CONFLICT),

    /** 409 — sửa bài đang {@code PUBLISHED}/{@code ARCHIVED} bằng PATCH (L42 chỉ cho sửa bản nháp). Tham số: {@code status}. */
    CONTENT_NOT_EDITABLE("content-not-editable", HttpStatus.CONFLICT),

    /** 409 — chuyển trạng thái không hợp lệ với trạng thái hiện tại. Tham số: {@code from}, {@code to}. */
    CONTENT_INVALID_STATE("content-invalid-state", HttpStatus.CONFLICT),

    /** 409 — {@code slug} + {@code locale} đã tồn tại (ràng buộc {@code uq_care_tip_slug_locale}). Tham số: {@code slug}, {@code locale}. */
    CONTENT_SLUG_TAKEN("content-slug-taken", HttpStatus.CONFLICT),

    /** 400 — thiếu {@code reason} dài ≥ 10 ký tự ở endpoint bắt buộc {@code Rsn} (p8 §8.3.2, p15 REQ-AUD-03). Tham số: {@code minLength}. */
    REASON_REQUIRED("reason-required", HttpStatus.BAD_REQUEST),

    /** 403 — thiếu vai trò quản trị nội dung (p8 §8.3.2 {@code R:ADMIN_CATALOG,ADMIN_SUPER,DPO}). Tham số: {@code required}. */
    ADMIN_ROLE_REQUIRED("admin-role-required", HttpStatus.FORBIDDEN),

    /**
     * 400 — tham số truy vấn sai giới hạn (ví dụ {@code limit > 100}) — p8 §8.2.4(a).
     *
     * <p>Mã này thuộc nhóm "Chung &amp; validate", tức là KHÔNG thuộc riêng content. Nó nằm ở đây
     * vì {@code shared} đã bị khoá sở hữu file với A3 và chưa có enum mã lỗi chung. W3 cần nhấc nó
     * lên {@code shared.error} rồi xoá bản sao trong từng module — xem
     * {@code docs/handovers/A3.md} mục "Việc còn lại của W3".</p>
     *
     * <p>⚠️ {@code cat.api.CatErrorCode} cũng khai báo {@code VALIDATION_FAILED}. Chưa vô hại vì
     * chưa module nào đăng ký bean, nhưng {@code ErrorCodeRegistry} ném
     * {@code IllegalStateException} khi hai enum khác nhau trả cùng một {@code code()} — nên phải
     * gộp nhóm chung TRƯỚC khi bật đăng ký.</p>
     */
    VALIDATION_FAILED("validation-failed", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ContentErrorCode(String slug, HttpStatus status) {
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
