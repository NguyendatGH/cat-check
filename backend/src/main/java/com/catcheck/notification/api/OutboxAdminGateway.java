package com.catcheck.notification.api;

import java.util.UUID;

/**
 * Cong cho L67 {@code POST /api/v1/admin/notifications/outbox/{id}/resend} (p8 §8.4.12 muc (f),
 * p14 §14.2.2 o Q26) — dua mot ban ghi outbox {@code FAILED} tro lai hang cho.
 *
 * <p><b>Vi sao module {@code admin} khong tu viet mot cau {@code UPDATE}:</b> {@code email_outbox}
 * va {@code notification_outbox} thuoc module {@code notification} (p4 F5), va quan trong hon,
 * toan bo luat retry/backoff/dedupe/dead-letter cua p12 §12.8.1 song o day. Mot cau
 * {@code UPDATE status='PENDING'} tu ben ngoai se bo qua het: no co the dat lai mot ban ghi da
 * {@code SENT}, co the pha {@code attempts} khien backoff 1p/5p/30p/2h/12h tinh lai tu dau, va
 * khong ai o {@code admin} biet rang {@code notification_outbox} con mot rang buoc
 * {@code UNIQUE (notification_id, push_subscription_id)}. Ghi nhan o handoff H15.106.</p>
 *
 * <p><b>Khong step-up, khong {@code reason}</b> — p8 L67 noi ro ly do: "chi day lai noi dung da
 * soan". Khong co du lieu moi nao duoc tao, khong co quyet dinh nghiep vu nao duoc dua; hanh dong
 * duy nhat la thu lai mot lan gui ma chinh he thong da soan va da that bai. Van ghi
 * {@code audit_log} ({@code Aud}) vi no la mot thao tac admin len du lieu cua nguoi dung.</p>
 */
public interface OutboxAdminGateway {

    /**
     * Dua ban ghi {@code id} tro lai {@code PENDING} voi {@code next_attempt_at = now}.
     *
     * <p>Tim o <b>ca hai</b> bang: id la UUID nen khong trung, va L66 tra ve mot danh sach da
     * hop nhat hai bang — nguoi bam nut o UI khong biet (va khong can biet) dong do thuoc bang
     * nao.</p>
     *
     * @return ket qua da phan loai; <b>khong nem</b> cho hai truong hop binh thuong
     *         (khong tim thay / khong phai {@code FAILED}) de controller quyet dinh ma HTTP
     */
    ResendOutcome resendFailed(UUID outboxId);

    /** Ket qua mot lan gui lai. */
    enum ResendOutcome {

        /** Da dat lai {@code PENDING}; job day outbox se nhan o lan chay ke tiep. */
        REQUEUED,

        /** Khong co dong nao mang id nay o ca hai bang outbox. */
        NOT_FOUND,

        /**
         * Co dong nhung trang thai khong phai {@code FAILED}. p8 L67 chi cho gui lai ban ghi
         * {@code FAILED}: mot dong {@code PENDING} <b>dang</b> cho gui (bam lai la vo nghia va
         * co the gay gui doi), con mot dong {@code SENT} thi gui lai la gui <b>lan thu hai</b>
         * mot thong bao nguoi dung da nhan.
         */
        NOT_FAILED
    }
}
