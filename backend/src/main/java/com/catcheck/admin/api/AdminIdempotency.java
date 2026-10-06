package com.catcheck.admin.api;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.UUID;

/**
 * Kiem header {@code Idempotency-Key} cho cac endpoint admin co ky hieu {@code Idem = !}
 * (p8 §8.1.8, §8.4.12).
 *
 * <p><b>Day CHI la buoc kiem hop dong, KHONG phai lop phat lai.</b> p8 §8.1.8 doi mot lop luu
 * {@code idempotency_record} (bang da ton tai tu {@code V15__ops.sql}) de cung mot key tra nguyen
 * ket qua cu, va {@code 409 IDEMPOTENCY_KEY_CONFLICT} khi cung key nhung noi dung khac. Lop do
 * <b>chua ton tai o bat ky module nao</b> cua repo — {@code scan} tu lam bang
 * {@code scan.idempotency_key} rieng, {@code credit} ghi chu la con thieu. Handoff H15.185.</p>
 *
 * <p><b>Vi sao van kiem du chua phat lai duoc:</b> doi header ngay tu bay gio khien client (admin
 * UI cua p14) phai sinh key ngay tu dau; neu de ngo roi bat sau thi moi client da viet se vo khi
 * lop phat lai duoc bat. Va rieng voi L72 — endpoint ghi mot ban ghi cho MOI user — mot key o
 * request la dieu kien can de sau nay <b>co the</b> chong duoc lan bam doi; khong co key thi
 * khong co gi de chong.</p>
 *
 * <p>Hai ma loi lay nguyen van p8 §8.2.4(a), nen chung <b>khong</b> duoc dang ky bean
 * {@code ErrorCode}: ten co the trung voi mot module khac dang ky truoc, va
 * {@code ErrorCodeRegistry} se lam app khong khoi dong duoc. {@code GlobalExceptionHandler} doc
 * {@code ErrorCode} truc tiep tu exception nen hanh vi khong doi — cung quy uoc da ghi o
 * {@code AdminApiErrorCode}.</p>
 */
public final class AdminIdempotency {

    private AdminIdempotency() {
    }

    /**
     * @throws BusinessRuleException {@code 400 IDEMPOTENCY_KEY_MISSING} neu thieu header,
     *                               {@code 400 IDEMPOTENCY_KEY_INVALID} neu khong phai UUID
     */
    public static UUID require(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new BusinessRuleException(Code.IDEMPOTENCY_KEY_MISSING);
        }
        try {
            return UUID.fromString(headerValue.strip());
        } catch (IllegalArgumentException ex) {
            // p8 §8.2.4(a) ghi "key khong phai UUID v4". Khong kiem rieng version 4: UUID v7
            // (chuan cua chinh du an, p4 §4.1.1) cung la mot key hop le va tot hon cho sap xep —
            // tu choi no se la mot luat tu phat minh, chat hon ca spec.
            throw new BusinessRuleException(Code.IDEMPOTENCY_KEY_INVALID);
        }
    }

    /** Hai ma cua p8 §8.2.4(a). Khong dang ky bean — xem javadoc lop. */
    private enum Code implements ErrorCode {

        IDEMPOTENCY_KEY_MISSING("idempotency-key-missing"),
        IDEMPOTENCY_KEY_INVALID("idempotency-key-invalid");

        private final String slug;

        Code(String slug) {
            this.slug = slug;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.BAD_REQUEST;
        }

        @Override
        public URI typeUri() {
            return URI.create("https://catcheck.vn/problems/" + slug);
        }
    }
}
