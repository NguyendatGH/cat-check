package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

public record AppSettingRow(
        String key,
        String value,
        String valueType,
        String description,
        Boolean secret,
        UUID updatedBy,
        Instant updatedAt
) {

    public static final String MASK = "••••••••";

    public AppSettingRow masked() {
        return Boolean.TRUE.equals(secret)
                ? new AppSettingRow(key, MASK, valueType, description, secret, updatedBy, updatedAt)
                : this;
    }
}
