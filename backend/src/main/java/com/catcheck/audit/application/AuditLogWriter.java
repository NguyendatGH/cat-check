package com.catcheck.audit.application;

import com.catcheck.audit.api.AuditEvent;

import java.util.Map;

public interface AuditLogWriter {

    void append(AuditEvent event, Map<String, Object> redactedMetadata,
                Map<String, Object> redactedBefore, Map<String, Object> redactedAfter);
}
