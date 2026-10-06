package com.catcheck.notification.api;

import java.util.Map;
import java.util.Set;

/**
 * Cong cho L72 {@code POST /api/v1/admin/system/broadcast} (p8 §8.4.12 muc (f), p14 §14.2.2
 * o Q30) — gui mot thong bao he thong toi <b>moi</b> chu the du dieu kien.
 *
 * <p><b>Vi sao L72 can mot co che rieng chu khong lap lai {@code enqueue} 10.000 lan tu
 * {@code admin}:</b> p12 §12.2.5 ghi thang rang {@code PRIVACY_INCIDENT_NOTICE} "can mot co che
 * ma Phase 1 chua co: gui HANG LOAT toi mot tap chu the duoc chon". Viec chon tap do
 * ({@code app_user.status} nao duoc gui, tai khoan {@code ANONYMIZED} thi khong) la kien thuc cua
 * module {@code notification} — no da co {@code NotificationRecipientPort} voi dung bo loc do.
 * Dat vong lap o {@code admin} nghia la nhan ban bo loc ay o mot module khong so huu no.</p>
 *
 * <p><b>Hai template, hai can cu phap ly, va chung KHONG thay nhau</b> (p12 §12.2.5):
 * {@code SYSTEM_MAINTENANCE} la thong bao dich vu; {@code PRIVACY_INCIDENT_NOTICE} la nghia vu
 * luat dinh voi han <b>72 gio</b> (Dieu 29.1.a ND356) va phai gui cho <b>moi</b> chu the bi anh
 * huong, "ke ca tai khoan dang {@code RESTRICTED}".</p>
 */
public interface SystemBroadcastGateway {

    /** Dung hai template nay duoc phep — p8 L72. Moi gia tri khac la loi cua ben goi. */
    Set<String> ALLOWED_TEMPLATE_CODES = Set.of("SYSTEM_MAINTENANCE", "PRIVACY_INCIDENT_NOTICE");

    /**
     * Phat mot thong bao he thong.
     *
     * @param templateCode {@code SYSTEM_MAINTENANCE} hoac {@code PRIVACY_INCIDENT_NOTICE}
     * @param dryRun       <b>chi dem nguoi nhan, khong ghi gi</b>. Khong phai tien nghi: L72 la
     *                     endpoint duy nhat trong he thong co the ghi mot ban ghi cho MOI user
     *                     chi bang mot request, nen phai co duong kiem thu ma khong gui — cung
     *                     tinh than {@code dry_run} ma p15 REQ-RET-01 doi o job xoa, va cung
     *                     khuon voi L58 {@code .../dry-run}
     * @return so nguoi nhan da (hoac se) duoc ghi, va so nguoi bi bo qua
     */
    BroadcastResult broadcast(String templateCode, String title, String body,
                              Map<String, Object> payload, boolean dryRun);

    /**
     * @param recipientsMatched so chu the du dieu kien nhan
     * @param notificationsQueued so ban ghi thuc su da ghi ({@code 0} khi {@code dryRun})
     * @param dedupeKeyPrefix   tien to khoa chong trung da dung — tra ve de ghi vao
     *                          {@code audit_log}, nho do mot lan bam lap lai truy nguoc duoc
     */
    record BroadcastResult(int recipientsMatched, int notificationsQueued, String dedupeKeyPrefix) {
    }
}
