package com.catcheck.audit.api;

/**
 * Cong ghi nhat ky dung, dung o tang application cua module khac.
 *
 * <p>Ghi trong CUNG transaction voi hanh dong nghiep vu (mac dinh
 * {@code Propagation.REQUIRED}, xem {@code AuditLogServiceImpl}) — p11 §11.11.1
 * vayen thoi diem su kien phai khop.</p>
 */
public interface AuditLogService {

    void record(AuditEvent event);
}
