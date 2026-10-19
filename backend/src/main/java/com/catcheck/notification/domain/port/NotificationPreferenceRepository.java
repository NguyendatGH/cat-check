package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.UserNotificationPreference;

import java.util.UUID;

public interface NotificationPreferenceRepository {

    UserNotificationPreference findByUserIdOrDefaults(UUID userId);
}
