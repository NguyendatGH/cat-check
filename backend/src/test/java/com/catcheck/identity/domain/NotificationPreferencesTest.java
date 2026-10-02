package com.catcheck.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotificationPreferences} — tám field theo p8 §8.5 / p4 F4.
 *
 * <p>Test này canh một thứ dễ trôi: giá trị mặc định phải khớp ĐÚNG cột {@code DEFAULT} của
 * {@code user_notification_preference} trong {@code V13__notification.sql}. Lệch nhau thì
 * tài khoản cũ (chưa có dòng, đọc ra mặc định của Java) và tài khoản mới (có dòng, đọc ra
 * mặc định của DB) hiển thị khác nhau ở cùng một màn hình.</p>
 */
class NotificationPreferencesTest {

    @Test
    void mac_dinh_khop_cot_default_cua_V13() {
        NotificationPreferences d = NotificationPreferences.defaults();

        assertEquals(AttentionAlertChannel.PUSH_AND_INAPP, d.attentionAlertChannel());
        assertTrue(d.creditAlertsEnabled());
        assertTrue(d.reportReadyEnabled());
        // Mặc định TẮT: mọi ảnh đều đến hạn 14 ngày nên bật mặc định là spam (p12 §12.2.3).
        assertFalse(d.imageRetentionWarningEnabled());
        // Thông báo kết quả bình thường — mặc định tắt (p4 F4).
        assertFalse(d.normalResultEnabled());
        assertTrue(d.quietHoursEnabled());
        assertEquals(LocalTime.of(22, 0), d.quietHoursStart());
        assertEquals(LocalTime.of(7, 0), d.quietHoursEnd());
    }

    @Test
    void khong_co_cach_nao_bieu_dien_trang_thai_tat_han_canh_bao() {
        // p12 §12.9.1 + quyết định #10: cảnh báo kết quả cần chú ý không tắt hoàn toàn được.
        // Với shape p8 thì đó là ràng buộc KIỂU — enum chỉ có đúng hai giá trị, nên không
        // dựng nổi một `NotificationPreferences` vi phạm. Test này giữ cho ai đó không lặng
        // lẽ thêm hằng thứ ba vào enum.
        assertEquals(2, AttentionAlertChannel.values().length);
    }

    @Test
    void gio_im_lang_vat_qua_nua_dem_la_trang_thai_hop_le() {
        // p4 F4 ghi rõ `start > end` là hợp lệ — đừng "sửa" thành khoảng thời gian thường.
        NotificationPreferences d = NotificationPreferences.defaults();
        assertTrue(d.quietHoursStart().isAfter(d.quietHoursEnd()));
    }
}
