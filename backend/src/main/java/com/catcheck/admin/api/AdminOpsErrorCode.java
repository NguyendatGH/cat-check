package com.catcheck.admin.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

/**
 * Ma loi cua nam endpoint van hanh moi — L65, L67, L69, L70, L72 (p8 §8.4.12 muc (f)).
 *
 * <p><b>Chi {@link #SETTING_KEY_UNKNOWN} co trong danh muc p8 §8.2.4(j).</b> Nam ma con lai
 * khong co trong p8 va <b>khong duoc tu coi la dung</b> — da ghi handoff H15.181 de p8 bo sung.
 * Ly do phai co chung thay vi don ve {@code NOT_FOUND}/{@code 409} tran: moi ma phan biet hai
 * tinh huong ma nguoi van hanh phai xu ly khac nhau, va go lan hai tinh huong do la cach de
 * nguoi truc di tim bug o cho khong co bug. Vi du {@link #JOB_NOT_FOUND} ("khong co job nao ten
 * vay — go sai chinh ta?") khac han {@link #JOB_NOT_MANUALLY_RUNNABLE} ("job co that va dang
 * chay theo lich, chi la chua bam duoc bang tay").</p>
 */
public enum AdminOpsErrorCode implements ErrorCode {

    /**
     * 404 — p8 §8.2.4(j), nguyen van: {@code app_setting.key} khong ton tai. Day la ma DUY NHAT
     * trong enum nay da co trong danh muc p8, va L70 ghi ro no.
     */
    SETTING_KEY_UNKNOWN("admin/setting-key-unknown", HttpStatus.NOT_FOUND),

    /**
     * 404 — {@code jobName} khong co trong danh muc job cua ban build nay
     * ({@code JobProperties.scheduledJobs()}).
     *
     * <p>Khong tra {@code 403} hay {@code 400}: p8 §8.2.5 muc 1 chot "khong ton tai ⇒ 404", va
     * danh muc job khong phai bi mat (p12 §12.6 la tai lieu cong khai cua du an) nen 404 khong
     * he lo gi.</p>
     */
    JOB_NOT_FOUND("admin/job-not-found", HttpStatus.NOT_FOUND),

    /**
     * 409 — job co trong danh muc nhung chua dang ky {@code ManualJobTrigger}. Tham so: danh
     * sach ten job hien bam duoc, de nguoi van hanh khong phai do.
     */
    JOB_NOT_MANUALLY_RUNNABLE("admin/job-not-manually-runnable", HttpStatus.CONFLICT),

    /**
     * 409 — cong tac tong {@code catcheck.jobs.enabled = false}. Phai la mot ma rieng chu khong
     * duoc im lang tra "thanh cong, 0 item": {@code JobRunner} co y khong ghi dong
     * {@code job_run} nao khi cong tac tat, nen mot lan bam se khong de lai dau vet gi va nguoi
     * bam se tuong job da chay.
     */
    JOBS_DISABLED("admin/jobs-disabled", HttpStatus.CONFLICT),

    /** 404 — khong co ban ghi outbox nao mang id nay o ca {@code email_outbox} lan {@code notification_outbox}. */
    OUTBOX_ENTRY_NOT_FOUND("admin/outbox-entry-not-found", HttpStatus.NOT_FOUND),

    /**
     * 409 — ban ghi ton tai nhung khong o trang thai {@code FAILED}. p8 L67 chi cho gui lai ban
     * ghi {@code FAILED}; {@code PENDING} thi dang cho gui (bam lai gay gui doi), {@code SENT}
     * thi gui lai la gui lan thu hai mot thong bao nguoi dung da nhan.
     */
    OUTBOX_ENTRY_NOT_FAILED("admin/outbox-entry-not-failed", HttpStatus.CONFLICT),

    /**
     * 422 — {@code templateCode} ngoai hai gia tri p8 L72 cho phep
     * ({@code SYSTEM_MAINTENANCE} | {@code PRIVACY_INCIDENT_NOTICE}).
     *
     * <p>{@code 422} chu khong {@code 400}: cu phap dung (mot chuoi hop le), rang buoc <b>nghiep
     * vu</b> sai — dung dinh nghia cua p8 §8.1.12 cho {@code 422}. Va day la rang buoc nghiep vu
     * dang ke: phat mot template khac qua duong nay se gui no cho moi user trong he thong.</p>
     */
    BROADCAST_TEMPLATE_NOT_ALLOWED("admin/broadcast-template-not-allowed", HttpStatus.UNPROCESSABLE_ENTITY);

    private static final String PROBLEM_BASE = "https://catcheck.vn/problems/";

    private final String slug;
    private final HttpStatus status;

    AdminOpsErrorCode(String slug, HttpStatus status) {
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
