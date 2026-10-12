package com.catcheck.shared.api.dto;

import java.util.Map;

public record SystemStatusView(
        String buildVersion,
        MaintenanceView maintenance,
        Map<String, String> features
) {

    public record MaintenanceView(boolean active, String until) {
    }
}
