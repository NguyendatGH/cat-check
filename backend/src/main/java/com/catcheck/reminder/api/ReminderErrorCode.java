package com.catcheck.reminder.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/** Mã lỗi nghiệp vụ của module {@code reminder} — tên/status theo p8 §8.2.4 và nhóm I. */
public enum ReminderErrorCode implements ErrorCode {

    /** 404 — không tồn tại HOẶC không thuộc user (p8 §8.2.5 quy tắc 1: không trả 403). */
    REMINDER_NOT_FOUND("reminder/not-found", HttpStatus.NOT_FOUND),

    /**
     * 409 — mèo này đã có một lịch cùng {@code type} đang bật. Ép ở DB bằng partial unique index
     * {@code uq_reminder_cat_type_active} (p4 F1, bất biến I23), không ép ở tầng service.
     *
     * <p>p8 §8.2.4 ghi rõ nghĩa cũ ("vượt 3 lịch/mèo") là SAI:
     * {@code app_setting['reminder.max_active_per_cat'] = 3} là 3 <i>loại</i>, không phải 3 lịch
     * cùng loại.</p>
     */
    REMINDER_LIMIT_REACHED("reminder/limit-reached", HttpStatus.CONFLICT),

    /** 400 — payload lịch không hợp lệ (thiếu intervalDays, điền cả rrule lẫn interval, kênh lạ…). */
    REMINDER_SCHEDULE_INVALID("reminder/schedule-invalid", HttpStatus.BAD_REQUEST),

    /** 404 — {@code catId} không tồn tại hoặc không thuộc user. Cùng lý do không trả 403. */
    CAT_NOT_FOUND("cat/not-found", HttpStatus.NOT_FOUND),

    /**
     * 403 — gói hiện tại không có tính năng {@code reminder}.
     *
     * <p>TRÙNG {@code code()} với {@code CreditErrorCode.FEATURE_NOT_IN_PLAN}, và
     * {@code CreditErrorCodeConfiguration} ĐÃ đăng ký bean {@code featureNotInPlan()}. Theo đúng
     * quy ước ghi ở {@code CatErrorCodeConfiguration}: giữ hằng để ném được, nhưng KHÔNG đăng ký
     * bean lần nữa — đăng ký trùng làm {@code ErrorCodeRegistry.validate} ném
     * {@code IllegalStateException} lúc khởi động.</p>
     */
    FEATURE_NOT_IN_PLAN("credit/feature-not-in-plan", HttpStatus.FORBIDDEN);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ReminderErrorCode(String slug, HttpStatus status) {
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
