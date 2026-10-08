package com.catcheck.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cong doc nhat ky dung cho man "Ai da truy cap du lieu cua toi" (B13): chi cac dong do
 * ADMIN/DPO thuc hien tren du lieu cua chinh nguoi dung do. Khong tra metadata/IP/UA
 * (tranh lo thong tin noi bo) — chi loai tac nhan, vai tro, hanh dong, ket qua, thoi diem.
 */
public interface AuditAccessLogQuery {

    List<Entry> accessesToUserData(UUID subjectUserId, int limit);

    record Entry(Instant occurredAt, String actorType, String actorRole, String action, String result) {
    }
}
