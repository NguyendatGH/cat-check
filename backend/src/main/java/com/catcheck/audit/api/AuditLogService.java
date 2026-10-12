package com.catcheck.audit.api;

public interface AuditLogService {

    void record(AuditEvent event);
}
