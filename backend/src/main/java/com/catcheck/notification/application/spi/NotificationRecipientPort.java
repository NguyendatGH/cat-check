package com.catcheck.notification.application.spi;

import com.catcheck.notification.domain.NotificationRecipient;

import java.util.Optional;
import java.util.UUID;

/**
 * Đọc {@code app_user.email} / {@code locale} / {@code timezone} để điền
 * {@code email_outbox.to_address} và tính quiet hours theo giờ địa phương (p12 §12.5.2).
 *
 * <p>Cùng lý do như {@link ConsentGatePort}: đọc bằng JDBC, không import {@code identity}.</p>
 */
public interface NotificationRecipientPort {

    Optional<NotificationRecipient> findById(UUID userId);
}
