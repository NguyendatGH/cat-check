package com.catcheck.audit.application;

import com.catcheck.audit.api.AuditEvent;

import java.util.Map;

/**
 * Cong ghi {@code audit_log}. Cong nay o tang application de {@code AuditLogServiceImpl}
 * khong phai import adapter JDBC cua chinh module (R2); trien khai o
 * {@code ..infrastructure.persistence}.
 */
public interface AuditLogWriter {

    void append(AuditEvent event, Map<String, Object> redactedMetadata,
                Map<String, Object> redactedBefore, Map<String, Object> redactedAfter);
}
