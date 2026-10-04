package com.catcheck.admin.api.dto;

import com.catcheck.shared.application.spi.AppSettingWriter;

import java.time.Instant;

public record AdminMaintenanceResponse(boolean active, String until, Instant updatedAt) {

    public static AdminMaintenanceResponse from(AppSettingWriter.Setting active, AppSettingWriter.Setting until) {
        return new AdminMaintenanceResponse(Boolean.parseBoolean(active.value()),
                until == null || until.value().isBlank() ? null : until.value(), active.updatedAt());
    }
}
