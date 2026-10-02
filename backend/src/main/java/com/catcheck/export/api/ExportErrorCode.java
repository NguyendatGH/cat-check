package com.catcheck.export.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/** Mã lỗi nghiệp vụ của module {@code export} — tên/status theo p8 §8.4.10 (nhóm J). */
public enum ExportErrorCode implements ErrorCode {

    /** 409 — user đã có job {@code QUEUED}/{@code RUNNING} (p13 §13.6.2, UNIQUE partial index). */
    EXPORT_JOB_IN_PROGRESS("export-job-in-progress", HttpStatus.CONFLICT),
    /** 422 — không có scan hợp lệ nào trong khoảng đã chọn. */
    EXPORT_NO_DATA("export-no-data", HttpStatus.UNPROCESSABLE_ENTITY),
    /** 404 — job không tồn tại hoặc không thuộc user. */
    EXPORT_JOB_NOT_FOUND("export-job-not-found", HttpStatus.NOT_FOUND),
    /** 409 — job chưa {@code READY} (còn {@code QUEUED}/{@code RUNNING}/{@code FAILED}). */
    EXPORT_NOT_READY("export-not-ready", HttpStatus.CONFLICT),
    /** 410 — file PDF đã quá hạn 7 ngày, job đã chuyển {@code EXPIRED}. */
    EXPORT_EXPIRED("export-expired", HttpStatus.GONE),
    /** 400 — khoảng thời gian không hợp lệ (quá 12 tháng, {@code from > to}, {@code to} ở tương lai). */
    EXPORT_RANGE_INVALID("export-range-invalid", HttpStatus.BAD_REQUEST);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    ExportErrorCode(String slug, HttpStatus status) {
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
