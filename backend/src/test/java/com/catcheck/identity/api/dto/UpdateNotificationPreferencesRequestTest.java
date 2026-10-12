package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.AttentionAlertChannel;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateNotificationPreferencesRequestTest {

    private static UpdateNotificationPreferencesRequest full() {
        return new UpdateNotificationPreferencesRequest(
                "INAPP_ONLY", true, false, true, false, true,
                LocalTime.of(22, 0), LocalTime.of(7, 0));
    }

    @Test
    void body_du_tam_field_map_dung_sang_record_mien() {
        NotificationPreferences p = UpdateNotificationPreferencesRequest.toPreferences(full());

        assertEquals(AttentionAlertChannel.INAPP_ONLY, p.attentionAlertChannel());
        assertEquals(true, p.creditAlertsEnabled());
        assertEquals(false, p.reportReadyEnabled());
        assertEquals(true, p.imageRetentionWarningEnabled());
        assertEquals(false, p.normalResultEnabled());
        assertEquals(true, p.quietHoursEnabled());
        assertEquals(LocalTime.of(22, 0), p.quietHoursStart());
        assertEquals(LocalTime.of(7, 0), p.quietHoursEnd());
    }

    @Test
    void thieu_mot_co_boolean_thi_400_chu_khong_mac_dinh_false() {
        var missing = new UpdateNotificationPreferencesRequest(
                "PUSH_AND_INAPP", null, true, false, false, true,
                LocalTime.of(22, 0), LocalTime.of(7, 0));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> UpdateNotificationPreferencesRequest.toPreferences(missing));
        assertEquals("creditAlertsEnabled", ex.args()[0]);
    }

    @Test
    void thieu_gio_im_lang_thi_400() {
        var missing = new UpdateNotificationPreferencesRequest(
                "PUSH_AND_INAPP", true, true, false, false, true, null, LocalTime.of(7, 0));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> UpdateNotificationPreferencesRequest.toPreferences(missing));
        assertEquals("quietHoursStart", ex.args()[0]);
    }

    @Test
    void kenh_canh_bao_la_gia_tri_la_thi_400_chu_khong_roi_ve_mac_dinh() {
        var off = new UpdateNotificationPreferencesRequest(
                "OFF", true, true, false, false, true, LocalTime.of(22, 0), LocalTime.of(7, 0));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> UpdateNotificationPreferencesRequest.toPreferences(off));
        assertEquals("attentionAlertChannel", ex.args()[0]);
    }

    @Test
    void thieu_kenh_canh_bao_thi_400() {
        var blank = new UpdateNotificationPreferencesRequest(
                null, true, true, false, false, true, LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertThrows(BusinessRuleException.class,
                () -> UpdateNotificationPreferencesRequest.toPreferences(blank));
    }
}
