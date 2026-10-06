package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.AppSettingRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong doc/ghi {@code app_setting} cho L69 {@code GET /admin/settings} va L70
 * {@code PATCH /admin/settings/{key}} (p8 §8.4.12 muc (f)).
 *
 * <p><b>Vi sao khong dung lai {@code shared.application.spi.AppSettingWriter}:</b> cong do phuc
 * vu L71 (bat/tat bao tri) nen no <b>tao khoa neu chua co</b> ({@code INSERT ... ON CONFLICT})
 * va <b>luon dat {@code secret = false}</b>. Ca hai deu sai voi L70: p8 chot "khoa la ⇒
 * {@code 404 SETTING_KEY_UNKNOWN}" — tuc la L70 <b>khong duoc</b> tao khoa moi (danh muc khoa
 * thuoc p4 §H3 + seed {@code R__seed_app_setting.sql}, khong phai thu admin tu them qua API) —
 * va ghi de {@code secret = false} se bien mot khoa dang che thanh khoa cong khai chi vi co
 * nguoi sua gia tri cua no.</p>
 *
 * <p><b>{@code value} di qua cong nay la chuoi, khong phai JSON object.</b> Cot la
 * {@code JSONB} (p4 §H3: "luon JSONB de chua duoc so, chuoi, mang, object") nhung API nhan va
 * tra mot <b>chuoi</b>, va adapter boc theo {@code value_type}. Ly do: admin UI render mot
 * {@code <input>} theo {@code value_type} (p4 §H3 noi dung cot nay de lam dung viec do), nen mot
 * cay JSON lap lung o giua chi tao them cho cho lech kieu.</p>
 */
public interface AppSettingAdminPort {

    /** L69 — moi khoa, sap theo {@code key}. Gia tri THO: che la viec cua tang application. */
    List<AppSettingRow> findAll();

    /** Mot khoa; rong = khoa khong ton tai ⇒ L70 tra {@code 404 SETTING_KEY_UNKNOWN}. */
    Optional<AppSettingRow> findByKey(String key);

    /**
     * L70 — doi gia tri cua mot khoa <b>da ton tai</b>. KHONG tao khoa moi, KHONG doi
     * {@code value_type}, KHONG doi {@code secret}.
     *
     * @param rawValue gia tri dang chuoi; adapter boc theo {@code value_type} cua chinh dong do
     * @return dong sau khi sua; rong neu khoa khong ton tai (khong nem — de application quyet ma)
     */
    Optional<AppSettingRow> updateValue(String key, String rawValue, UUID updatedBy);
}
