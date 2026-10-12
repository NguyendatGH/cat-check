package com.catcheck.audit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogWriter writer;
    private final PiiRedactor redactor;

    public AuditLogServiceImpl(AuditLogWriter writer, PiiRedactor redactor) {
        this.writer = writer;
        this.redactor = redactor;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEvent event) {
        Map<String, Object> metadata = withDeniedAction(event);
        writer.append(event,
                redactor.redact(metadata),
                redactor.redact(event.before()),
                redactor.redact(event.after()));
    }

    private Map<String, Object> withDeniedAction(AuditEvent event) {
        Map<String, Object> metadata = new LinkedHashMap<>(event.metadata());
        if (event.outcome() == AuditOutcome.DENIED) {
            metadata.putIfAbsent("action", event.action());
        }
        return metadata;
    }
}
