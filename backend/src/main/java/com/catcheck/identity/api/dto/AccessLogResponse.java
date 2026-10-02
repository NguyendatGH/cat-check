package com.catcheck.identity.api.dto;

import java.util.List;

/** B13 — nhật ký truy cập dữ liệu cá nhân (stub, W3 nối AuditLogService) (p8 §8.4.4 nhóm B). */
public record AccessLogResponse(
        List<AccessLogEntry> items) {
}
