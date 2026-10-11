package com.catcheck.scan.application.spi;

import java.util.UUID;

/**
 * Consent {@code SCAN_IMAGE_RETAIN} ("Lưu ảnh quét trong 14 ngày") của người dùng đang hiệu lực
 * hay không.
 *
 * <p>Đây là mục đích TUỲ CHỌN, dữ liệu nhạy cảm, mặc định tắt
 * ({@code db/seed/R__seed_consent_purpose.sql}); gói có {@code storeImage} chưa đủ để lưu ảnh.
 * Không có consent thì {@code scan.store_image = false}, lý do {@code CONSENT_OFF}.</p>
 *
 * <p>SPI riêng của scan + adapter JDBC đọc {@code consent_record}, cùng cách
 * {@code notification.application.spi.ConsentGatePort}: {@code scan/package-info.java} không cho
 * phụ thuộc {@code privacy} ngoài {@code privacy::spi}, nên không import type nào của privacy.</p>
 */
public interface ImageRetentionConsentPort {

    /** Dòng {@code consent_record} mới nhất của (user, {@code SCAN_IMAGE_RETAIN}) là {@code GRANTED}. */
    boolean isGranted(UUID userId);
}
