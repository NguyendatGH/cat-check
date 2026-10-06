package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot dong {@code app_setting} (p4 §H3) nhu man L69/L70 can nhin thay.
 *
 * <p><b>{@code value} o day la gia tri THO, chua che.</b> Viec che la quyet dinh cua tang tren
 * ({@code AdminSettingsService}) chu khong phai cua adapter: L70 phai so {@code value} cu voi
 * {@code value} moi de ghi {@code before}/{@code after} vao {@code audit_log}, va mot gia tri da
 * che thi khong so duoc. Doi lai, moi duong ra API <b>phai</b> di qua
 * {@link #masked()} — xem javadoc o day.</p>
 *
 * @param secret {@code true} = p4 §H3 "che gia tri trong UI va trong audit"
 */
public record AppSettingRow(
        String key,
        String value,
        String valueType,
        String description,
        Boolean secret,
        UUID updatedBy,
        Instant updatedAt
) {

    /** Gia tri thay the cho khoa {@code secret = true} — do dai co dinh, khong he lo do dai that. */
    public static final String MASK = "••••••••";

    /**
     * Ban sao an toan de tra ra API: khoa {@code secret} bi thay gia tri bang {@link #MASK}.
     *
     * <p>p4 §H3 ghi {@code secret = true} nghia la "che gia tri trong UI va trong audit", va ghi
     * chu nghiep vu noi ro day KHONG phai cho bi mat that (FCM key, DB password — chung thuoc
     * bien moi truong, p18) ma cho "gia tri cau hinh nhay cam muc thap". Nen muc do che o day
     * dung muc do p4 doi: an gia tri, <b>giu</b> ten khoa, kieu, mo ta va moc sua — vi mot admin
     * van phai thay duoc "khoa nay ton tai va vua bi doi luc nao".</p>
     */
    public AppSettingRow masked() {
        return Boolean.TRUE.equals(secret)
                ? new AppSettingRow(key, MASK, valueType, description, secret, updatedBy, updatedAt)
                : this;
    }
}
