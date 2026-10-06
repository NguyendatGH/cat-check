package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Than cua L72 {@code POST /admin/system/broadcast} (p8 §8.4.12 muc (f)).
 *
 * @param templateCode {@code SYSTEM_MAINTENANCE} | {@code PRIVACY_INCIDENT_NOTICE} — p8 L72 chot
 *                     dung hai gia tri. Gia tri la ⇒
 *                     {@code 422 BROADCAST_TEMPLATE_NOT_ALLOWED}
 * @param title        tieu de da render. p12 §12.2.1 cam banner nhac gia tri pH/phan loai; o day
 *                     khong co du lieu suc khoe nen rang buoc do khong phat sinh, nhung tran do
 *                     dai van can vi title di vao banner push
 * @param body         noi dung. Voi {@code PRIVACY_INCIDENT_NOTICE}, p12 §12.2.5 doi du 6 muc cua
 *                     Dieu 29.2 ND356 (thoi diem &amp; hinh thuc phat hien; loai du lieu bi anh
 *                     huong; muc do nghiem trong &amp; rui ro; bien phap da/dang/se thuc hien;
 *                     huong dan tu phong ngua; lien he bo phan BVDLCN) — <b>khong</b> kiem duoc
 *                     bang code, nen tran 4000 ky tu la de noi dung do vua, khong phai de ep
 * @param payload      tham so render; {@code deepLink} nam trong day va p12 §12.2.7 bat buoc no
 *                     phai la mot route that cua p9 §9.4
 * @param dryRun       chi dem nguoi nhan, KHONG ghi gi. Xem javadoc
 *                     {@code SystemBroadcastGateway#broadcast} ve ly do truong nay ton tai
 * @param reason       ky hieu {@code Rsn} cua p8 §8.3.2
 */
public record SystemBroadcastRequest(
        @NotBlank @Size(max = 48) String templateCode,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4_000) String body,
        Map<String, Object> payload,
        Boolean dryRun,
        @NotBlank @Size(max = 500) String reason) {

    public SystemBroadcastRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public boolean dryRunOrFalse() {
        return Boolean.TRUE.equals(dryRun);
    }
}
