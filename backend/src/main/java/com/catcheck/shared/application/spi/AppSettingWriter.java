package com.catcheck.shared.application.spi;

import java.time.Instant;
import java.util.UUID;

public interface AppSettingWriter {

    Setting updateBoolean(String key, boolean value, String description, UUID updatedBy);

    Setting updateString(String key, String value, String description, UUID updatedBy);

    record Setting(String key, String value, String valueType, Instant updatedAt) {
    }
}
