package com.catcheck.audit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trien khai {@link AuditLogService}.
 *
 * <p>{@code Propagation.REQUIRED} (mac dinh) de dong audit nam trong cung
 * transaction voi hanh dong nghiep vu: p11 §11.11.1 vayen su kien "thoi diem ghi
 * phai khop". Hanh dong rollback se lam dong audit cung rollback — dung, vi day
 * la nhat ky su kien chu khong phai nhat ky "da thu".</p>
 *
 * <p>Khong bat buoc {@code @Transactional} o day: caller da co transaction,
 * Spring se tham gia. Nhung khi co caller khong mo transaction (vi du job nen),
 * {@code AuditLogWriter} tu co annotation rieng.</p>
 */
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

    /**
     * CHECK {@code ck_audit_denied_has_action} yeu cau {@code result = 'DENIED'} phai
     * kem {@code metadata.action}. Chen san o day de caller khong phai nho, va de
     * thong diep khi DB bao loi ra mo hinh la "het cho nho" hon la "sai cau truc".
     */
    private Map<String, Object> withDeniedAction(AuditEvent event) {
        Map<String, Object> metadata = new LinkedHashMap<>(event.metadata());
        if (event.outcome() == AuditOutcome.DENIED) {
            metadata.putIfAbsent("action", event.action());
        }
        return metadata;
    }
}
