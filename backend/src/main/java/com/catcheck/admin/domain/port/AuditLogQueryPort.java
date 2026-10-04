package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.AuditLogRow;

import java.util.List;
import java.util.UUID;

public interface AuditLogQueryPort {
    List<AuditLogRow> find(String action, String result, String actorType, UUID actorId, int offset, int limit);

    long count(String action, String result, String actorType, UUID actorId);
}
