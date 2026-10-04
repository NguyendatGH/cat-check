package com.catcheck.notification.application.spi;

import java.util.UUID;

/**
 * Consent gate cho push/email (p12 §12.1).
 *
 * <p><b>Vì sao là SPI của chính module này chứ không import {@code privacy}:</b>
 * {@code privacy/package-info.java} đã khai {@code allowedDependencies = {..., "notification", ...}}
 * — privacy → notification. Nếu notification lại import {@code privacy.domain.port.ConsentStatePort}
 * thì đồ thị module có chu trình và {@code ModularityTests} đỏ. Cùng judgment call đã ghi ở
 * {@code reminder/package-info.java}: đọc thẳng bảng bằng JDBC qua adapter trong
 * {@code infrastructure.persistence}, không import type Java của module khác.</p>
 */
public interface ConsentGatePort {

    /**
     * Đang hiệu lực = dòng mới nhất của cặp {@code (user, purpose)} trong {@code consent_record}
     * là {@code GRANTED}. Chưa có dòng nào ⇒ {@code false} — "im lặng không phải là đồng ý"
     * (p15 §15.3.1 C4).
     */
    boolean isGranted(UUID userId, String purposeCode);
}
