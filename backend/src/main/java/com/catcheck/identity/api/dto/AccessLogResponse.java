package com.catcheck.identity.api.dto;

import java.util.List;

public record AccessLogResponse(
        List<AccessLogEntry> items) {
}
