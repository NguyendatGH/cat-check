package com.catcheck.cat.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Mã lỗi nghiệp vụ của module cat, ánh xạ đúng bảng p8 §8.2.4(d) "Hồ sơ mèo".
 *
 * <p><b>Không khai báo bean.</b> {@code shared.error.ErrorCodeRegistry} nhận
 * {@code List<ErrorCode>} qua constructor; nếu mỗi module cùng expose một {@code @Bean List<ErrorCode>}
 * thì Spring không còn bean định danh duy nhất và app không khởi động. Cơ chế gộp thuộc W3.</p>
 *
 * <p><b>Cảnh báo W3 — trùng mã sẽ chặn app khởi động.</b> {@code ErrorCodeRegistry.validate} ném
 * {@code IllegalStateException} khi hai enum khác nhau cùng trả một {@code code()}. Mấy hằng số cụt
 * dưới đây ({@link #VALIDATION_FAILED}, {@link #RESOURCE_MODIFIED}, {@link #PAYLOAD_TOO_LARGE},
 * {@link #AVATAR_INVALID}) thuộc nhóm "Chung" của p8 và đã bị {@code content} khai báo lần nữa. Hôm
 * nay còn vô hại vì không module nào đăng ký bean, nhưng <b>trước khi bật đăng ký phải gộp nhóm
 * chung về một enum duy nhất</b> — xem {@code docs/handovers/A3.md}.</p>
 *
 * <p><b>404 thay vì 403 khi không sở hữu</b> (p8 §8.3.4): truy vấn lọc theo {@code owner_id} ngay
 * trong SQL nên hồ sơ của người khác đơn giản là không tồn tại. Trả 403 sẽ xác nhận id đó có thật và
 * mở lời mời dò id.</p>
 */
public enum CatErrorCode implements ErrorCode {

    // ---- nhóm (d) của p8 §8.2.4: mã riêng của hồ sơ mèo

    /** 404 — không tồn tại HOẶC không thuộc người gọi. */
    CAT_NOT_FOUND("cat/not-found", HttpStatus.NOT_FOUND),

    /**
     * 409 — vượt {@code user_entitlement.max_cat_profiles} (p5 R5).
     *
     * <p>Kiểm TRƯỚC {@link #CAT_HARD_LIMIT_REACHED} vì thứ tự này quyết định câu CTA hiện ra là
     * "nâng gói" hay "liên hệ hỗ trợ" — kiểm sai thứ tự thì người dùng bị đẩy vào ngõ cụt.
     * Tham số: {@code max}, {@code current}, {@code requiredPackage}.</p>
     */
    CAT_PROFILE_LIMIT_REACHED("cat/profile-limit-reached", HttpStatus.CONFLICT),

    /**
     * 409 — vượt trần cứng {@code app_setting['cat.max_per_user']} (C31, mặc định 8).
     *
     * <p>Tham số {@code max} để câu chữ đọc giá trị đang cấu hình, không in cứng số 8: trần là cấu
     * hình, đổi được mà không cần deploy.</p>
     */
    CAT_HARD_LIMIT_REACHED("cat/hard-limit-reached", HttpStatus.CONFLICT),

    /** 409 — xoá hồ sơ đã ở trạng thái đã xoá. */
    CAT_ALREADY_DELETED("cat/already-deleted", HttpStatus.CONFLICT),

    /**
     * 409 — D6 gọi {@code archive} cho mèo đã lưu trữ.
     *
     * <p>Mã này do chính bảng endpoint p8 §8.4.4 nêu ở dòng D6 nhưng lại KHÔNG có trong bảng danh
     * mục §8.2.4(d). Theo thứ tự thẩm quyền thì §8.4.4 (chi tiết hợp đồng) dùng rõ tên mã nên lấy
     * theo; ghi vào đây để lần sau tra cứu không phải đoán.</p>
     */
    CAT_ALREADY_ARCHIVED("cat/already-archived", HttpStatus.CONFLICT),

    /** 400 — {@code breedCode} không có trong {@code cat_breed} hoặc {@code active = false}. */
    CAT_BREED_UNKNOWN("cat/breed-unknown", HttpStatus.BAD_REQUEST),

    /** 400 — gửi cả {@code birthDate} lẫn {@code approxAgeMonths}. */
    CAT_AGE_CONFLICT("cat/age-conflict", HttpStatus.BAD_REQUEST),

    /** 422 — {@code questionnaireVersion} không còn được nhận. Tham số: {@code currentVersion}. */
    SURVEY_VERSION_UNSUPPORTED("cat/survey-version-unsupported", HttpStatus.UNPROCESSABLE_ENTITY),

    /** 404 — ghi chú không tồn tại / của mèo người khác / đã xoá mềm. */
    CAT_NOTE_NOT_FOUND("cat/note-not-found", HttpStatus.NOT_FOUND),

    /** 403 — sửa hoặc xoá ghi chú do người khác viết. */
    CAT_NOTE_NOT_EDITABLE("cat/note-not-editable", HttpStatus.FORBIDDEN),

    /** 409 — thao tác ghi trên mèo {@code status = ARCHIVED}. Tham số: {@code catId}. */
    CAT_ARCHIVED("cat/archived", HttpStatus.CONFLICT),

    /** 409 — đặt mèo chính cho con đang đã là mèo chính. */
    CAT_ALREADY_PRIMARY("cat/already-primary", HttpStatus.CONFLICT),

    /** 409 — đặt mèo chính cho mèo {@code ARCHIVED} hoặc đã xoá mềm. */
    CAT_PRIMARY_REQUIRES_ACTIVE("cat/primary-requires-active", HttpStatus.CONFLICT),

    /** 400 — {@code signs[]} rỗng hoặc có giá trị ngoài tập p4 C5. Tham số: {@code allowed[]}. */
    CLINICAL_SIGN_INVALID("cat/clinical-sign-invalid", HttpStatus.BAD_REQUEST),

    /** 404 — mèo chưa có bản khảo sát nào (D15). */
    SURVEY_NOT_FOUND("cat/survey-not-found", HttpStatus.NOT_FOUND),

    /**
     * 404 — D9 ({@code GET /cats/{id}/avatar}) khi mèo chưa gắn ảnh đại diện.
     *
     * <p>Bổ sung so với bản gốc của A3: {@code CatAvatarService#openAvatar} đã tham chiếu
     * {@code 404 AVATAR_NOT_FOUND} trong javadoc nhưng hằng số này chưa từng tồn tại — kiểm tra
     * bằng {@code grep -rn "AVATAR_NOT_FOUND" src/main/java} xác nhận không trùng với module nào
     * khác trước khi thêm.</p>
     */
    AVATAR_NOT_FOUND("cat/avatar-not-found", HttpStatus.NOT_FOUND),

    // ---- nhóm "Chung" của p8 §8.2.4 — xem cảnh báo trùng mã ở trên

    /** 400 — dùng {@code errors[]} trong body để chỉ từng ô sai. */
    VALIDATION_FAILED("common/validation-failed", HttpStatus.BAD_REQUEST),

    /** 412 — {@code If-Match} không khớp {@code ETag}. Tham số: {@code currentEtag}. */
    RESOURCE_MODIFIED("common/resource-modified", HttpStatus.PRECONDITION_FAILED),

    /** 413 — body vượt trần chung. Tham số: {@code maxBytes}. */
    PAYLOAD_TOO_LARGE("common/payload-too-large", HttpStatus.PAYLOAD_TOO_LARGE),

    /**
     * 400 — ảnh sai định dạng hoặc kích thước (p8 §8.2.4(c)).
     *
     * <p>Tham số {@code maxBytes}, {@code allowedTypes[]}. Khai báo ở đây chứ không dùng
     * {@code MediaErrorCode.STORAGE_UNSUPPORTED_TYPE} vì p8 đã chốt tên mã này cho người dùng thấy;
     * mã của media là chi tiết hạ tầng và không nên lọt ra ngoài.</p>
     */
    AVATAR_INVALID("common/avatar-invalid", HttpStatus.BAD_REQUEST),

    /** 403 — {@code status = RESTRICTED}, chỉ được đọc. Tham số: {@code restrictedAt}. */
    ACCOUNT_RESTRICTED("common/account-restricted", HttpStatus.FORBIDDEN),

    /** 400 — {@code sort} trỏ ngoài whitelist. Tham số: {@code allowed[]}. */
    SORT_FIELD_NOT_ALLOWED("common/sort-field-not-allowed", HttpStatus.BAD_REQUEST),

    /**
     * 403 — tinh nang khong co trong {@code user_entitlement.features} (p5 R5, p8 §8.2.4).
     * Dung cho D13 {@code GET /cats/{catId}/trends} (`E:trend`). Tham so: {@code feature}.
     *
     * <p>TRUNG ten hang voi {@code CreditErrorCode.FEATURE_NOT_IN_PLAN}, va credit ĐÃ đăng ký
     * bean {@code featureNotInPlan()}. Theo đúng quy ước ghi ở {@code CatErrorCodeConfiguration}
     * cho {@code AVATAR_INVALID}: giữ hằng số ở đây để application NÉM được mà không phải phụ
     * thuộc {@code credit::api}, nhưng KHÔNG đăng ký bean lần hai — đăng ký trùng tên làm
     * {@code BeanDefinitionOverrideException} và context sập lúc khởi động (đã gặp thật).</p>
     */
    FEATURE_NOT_IN_PLAN("common/feature-not-in-plan", HttpStatus.FORBIDDEN);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    CatErrorCode(String slug, HttpStatus status) {
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
