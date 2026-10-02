package com.catcheck.cat.infrastructure;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link CatErrorCode} vào {@code shared.error.ErrorCodeRegistry}.
 *
 * <p><b>Tại sao không dùng {@code @Bean List<ErrorCode>}</b>: xem javadoc của
 * {@code CreditErrorCodeConfiguration}/{@code IdentityErrorCodeConfiguration}/
 * {@code PrivacyErrorCodeConfiguration} — {@code ErrorCodeRegistry} nhận {@code List<ErrorCode>}
 * qua constructor và Spring gom mọi bean có kiểu {@code ErrorCode}; một {@code @Bean
 * List<ErrorCode>} không bao giờ được gom vào đó. Nên mỗi hằng số đăng ký thành MỘT bean
 * {@code ErrorCode} riêng.</p>
 *
 * <p><b>Đối chiếu trùng mã (việc còn lại A3 để ngỏ, xem {@code docs/handovers/A3.md} và
 * {@code docs/handovers/A3-backend-fix.md}):</b> đã <i>grep</i> {@code code()}/tên hằng của
 * {@link CatErrorCode} so với toàn bộ {@code *ErrorCode.java} + {@code *ErrorCodeConfiguration.java}
 * hiện có (identity, credit, privacy, content, media, colorchart). Kết quả:</p>
 * <ul>
 *   <li>{@link CatErrorCode#AVATAR_INVALID} — TRÙNG {@code code()="AVATAR_INVALID"} với
 *       {@code IdentityErrorCode.AVATAR_INVALID}, và {@code IdentityErrorCodeConfiguration} ĐÃ
 *       đăng ký bean đó ({@code avatarInvalid()}). {@code PrivacyErrorCode.AVATAR_INVALID} cũng
 *       tồn tại nhưng privacy CHỦ ĐỘNG không đăng ký (đúng theo cùng lý do). Cat làm giống privacy:
 *       giữ hằng số để domain/application NÉM được, nhưng KHÔNG đăng ký bean ở đây — đăng ký thêm
 *       một lần nữa sẽ làm {@code ErrorCodeRegistry.validate} ném {@code IllegalStateException} khi
 *       khởi động.</li>
 *   <li>{@link CatErrorCode#VALIDATION_FAILED}, {@link CatErrorCode#RESOURCE_MODIFIED},
 *       {@link CatErrorCode#PAYLOAD_TOO_LARGE} — TRÙNG tên hằng với {@code ContentErrorCode}
 *       (và {@code VALIDATION_FAILED} còn trùng cả với {@code PrivacyErrorCode}), nhưng
 *       <b>KHÔNG module nào đăng ký bean nào trong ba mã này</b> (đã grep toàn bộ
 *       {@code *ErrorCodeConfiguration.java}: identity/credit/privacy đều không có
 *       {@code resourceModified()}/{@code payloadTooLarge()}, và không ai đăng ký
 *       {@code validationFailed()}). Vì đây là ba mã "chung" nhiều module cùng khai trùng tên mà
 *       chưa có nơi tập trung (đúng như {@code ContentErrorCode} tự ghi nhận — "W3 cần nhấc nó lên
 *       {@code shared.error}"), quyết định ở đây là <b>KHÔNG đăng ký</b> từ phía cat nữa: nếu cat
 *       đăng ký trước, một agent khác lỡ thêm {@code ContentErrorCodeConfiguration} sau này với
 *       cùng tên hằng sẽ làm context sập — giữ nguyên trạng "chưa ai đăng ký" là lựa chọn an toàn
 *       nhất cho tới khi W3 gộp bốn mã này về một enum {@code shared.error.CommonErrorCode} dùng
 *       chung. Các exception ném {@code CatErrorCode.VALIDATION_FAILED} vẫn hoạt động bình thường:
 *       {@code GlobalExceptionHandler} đọc {@code errorCode.code()}/{@code status()}/{@code typeUri()}
 *       trực tiếp trên instance bị ném, KHÔNG tra qua registry (registry chỉ kiểm trùng lúc khởi
 *       động, không phải bảng tra runtime).</li>
 *   <li>{@link CatErrorCode#ACCOUNT_RESTRICTED} — trùng TÊN với {@code IdentityErrorCode
 *       .ACCOUNT_RESTRICTED} nhưng KHÔNG trùng {@code code()} theo nghĩa xung đột: hai enum khác
 *       nhau vẫn có {@code name() == "ACCOUNT_RESTRICTED"} nên {@code code()} (= {@code name()})
 *       THỰC SỰ trùng nhau. Vì {@code IdentityErrorCodeConfiguration} đã đăng ký hằng này
 *       ({@code accountRestricted()}), cat KHÔNG đăng ký lại — dùng chung ý nghĩa "tài khoản
 *       RESTRICTED chỉ đọc" nên việc identity đã đăng ký là đủ.</li>
 *   <li>Mười hằng còn lại (nhóm (d) — {@code CAT_*}, {@code SURVEY_*}, {@code CLINICAL_SIGN_INVALID},
 *       {@code SORT_FIELD_NOT_ALLOWED}, {@code AVATAR_NOT_FOUND}) không trùng bất kỳ mã nào đã
 *       đăng ký ⇒ đăng ký bình thường, đúng khuôn {@code CreditErrorCodeConfiguration}.</li>
 * </ul>
 */
@Configuration
public class CatErrorCodeConfiguration {

    @Bean
    ErrorCode catNotFound() {
        return CatErrorCode.CAT_NOT_FOUND;
    }

    @Bean
    ErrorCode catProfileLimitReached() {
        return CatErrorCode.CAT_PROFILE_LIMIT_REACHED;
    }

    @Bean
    ErrorCode catHardLimitReached() {
        return CatErrorCode.CAT_HARD_LIMIT_REACHED;
    }

    @Bean
    ErrorCode catAlreadyDeleted() {
        return CatErrorCode.CAT_ALREADY_DELETED;
    }

    @Bean
    ErrorCode catAlreadyArchived() {
        return CatErrorCode.CAT_ALREADY_ARCHIVED;
    }

    @Bean
    ErrorCode catBreedUnknown() {
        return CatErrorCode.CAT_BREED_UNKNOWN;
    }

    @Bean
    ErrorCode catAgeConflict() {
        return CatErrorCode.CAT_AGE_CONFLICT;
    }

    @Bean
    ErrorCode surveyVersionUnsupported() {
        return CatErrorCode.SURVEY_VERSION_UNSUPPORTED;
    }

    @Bean
    ErrorCode catNoteNotFound() {
        return CatErrorCode.CAT_NOTE_NOT_FOUND;
    }

    @Bean
    ErrorCode catNoteNotEditable() {
        return CatErrorCode.CAT_NOTE_NOT_EDITABLE;
    }

    @Bean
    ErrorCode catArchived() {
        return CatErrorCode.CAT_ARCHIVED;
    }

    @Bean
    ErrorCode catAlreadyPrimary() {
        return CatErrorCode.CAT_ALREADY_PRIMARY;
    }

    @Bean
    ErrorCode catPrimaryRequiresActive() {
        return CatErrorCode.CAT_PRIMARY_REQUIRES_ACTIVE;
    }

    @Bean
    ErrorCode clinicalSignInvalid() {
        return CatErrorCode.CLINICAL_SIGN_INVALID;
    }

    @Bean
    ErrorCode surveyNotFound() {
        return CatErrorCode.SURVEY_NOT_FOUND;
    }

    @Bean
    ErrorCode avatarNotFound() {
        return CatErrorCode.AVATAR_NOT_FOUND;
    }

    @Bean
    ErrorCode sortFieldNotAllowed() {
        return CatErrorCode.SORT_FIELD_NOT_ALLOWED;
    }
}
