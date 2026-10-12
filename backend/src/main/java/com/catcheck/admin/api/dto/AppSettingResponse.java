package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.AppSettingRow;

import java.time.Instant;

public record AppSettingResponse(
        String key,
        String value,
        String valueType,
        String description,
        Boolean secret,
        Instant updatedAt,
        String etag) {

    public static AppSettingResponse from(AppSettingRow maskedRow, String etag) {
        return new AppSettingResponse(
                maskedRow.key(), maskedRow.value(), maskedRow.valueType(), maskedRow.description(),
                maskedRow.secret(), maskedRow.updatedAt(), etag);
    }
}
