package com.catcheck.insight.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/** Mã lỗi nghiệp vụ của module {@code insight} — tên/status theo p8 §8.4.7 (nhóm G1-G3). */
public enum InsightErrorCode implements ErrorCode {

    /** 404 — không tồn tại hoặc không thuộc mèo của user đang đăng nhập. */
    HEALTH_FLAG_NOT_FOUND("health-flag-not-found", HttpStatus.NOT_FOUND),
    /** 409 — race hiếm giữa hai lần khai dấu hiệu lâm sàng cùng lúc (UNIQUE dedupe_key). */
    HEALTH_FLAG_DUPLICATE("health-flag-duplicate", HttpStatus.CONFLICT);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    InsightErrorCode(String slug, HttpStatus status) {
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
