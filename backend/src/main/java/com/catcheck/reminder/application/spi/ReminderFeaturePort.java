package com.catcheck.reminder.application.spi;

import java.util.UUID;

/**
 * Cổng kiểm tra tính năng {@code reminder} có nằm trong gói hiện tại không (p8 §8.4.9 — mọi
 * endpoint I1–I6 đều gắn {@code E:reminder}).
 *
 * <p>Đọc cột JSONB {@code user_entitlement.features} bằng JDBC, cùng khuôn
 * {@code cat/application/spi/FeatureEntitlementPort}. Không import {@code credit.domain.PlanFeature}:
 * {@code credit} chỉ expose named interface {@code credit::api} còn type đó nằm ở
 * {@code credit.domain}.</p>
 *
 * <p>p11 §11.5.5: KHÔNG cache kết quả trong phiên — mỗi request kiểm lại.</p>
 */
public interface ReminderFeaturePort {

    boolean isReminderEnabled(UUID userId);
}
