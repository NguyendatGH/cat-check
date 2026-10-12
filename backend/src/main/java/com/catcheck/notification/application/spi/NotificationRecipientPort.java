package com.catcheck.notification.application.spi;

import com.catcheck.notification.domain.NotificationRecipient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRecipientPort {

    Optional<NotificationRecipient> findById(UUID userId);

    List<UUID> findActiveRecipientIdsAfter(UUID afterId, int limit);
}
