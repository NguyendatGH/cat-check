package com.catcheck.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferencesTest {

    @Test
    void mac_dinh_khop_cot_default_cua_V13() {
        NotificationPreferences d = NotificationPreferences.defaults();

        assertEquals(AttentionAlertChannel.PUSH_AND_INAPP, d.attentionAlertChannel());
        assertTrue(d.creditAlertsEnabled());
        assertTrue(d.reportReadyEnabled());
        assertFalse(d.imageRetentionWarningEnabled());
        assertFalse(d.normalResultEnabled());
        assertTrue(d.quietHoursEnabled());
        assertEquals(LocalTime.of(22, 0), d.quietHoursStart());
        assertEquals(LocalTime.of(7, 0), d.quietHoursEnd());
    }

    @Test
    void khong_co_cach_nao_bieu_dien_trang_thai_tat_han_canh_bao() {
        assertEquals(2, AttentionAlertChannel.values().length);
    }

    @Test
    void gio_im_lang_vat_qua_nua_dem_la_trang_thai_hop_le() {
        NotificationPreferences d = NotificationPreferences.defaults();
        assertTrue(d.quietHoursStart().isAfter(d.quietHoursEnd()));
    }
}
