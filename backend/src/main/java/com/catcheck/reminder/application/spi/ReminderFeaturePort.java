package com.catcheck.reminder.application.spi;

import java.util.UUID;

public interface ReminderFeaturePort {

    boolean isReminderEnabled(UUID userId);
}
