package com.catcheck.shared.application.spi;

import java.time.Instant;
import java.util.UUID;

/** Ghi các khoá cấu hình công khai do admin vận hành kiểm soát. */
public interface AppSettingWriter {

    Setting updateBoolean(String key, boolean value, String description, UUID updatedBy);

    Setting updateString(String key, String value, String description, UUID updatedBy);

    record Setting(String key, String value, String valueType, Instant updatedAt) {
    }
}
