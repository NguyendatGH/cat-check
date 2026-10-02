package com.catcheck.identity.domain.port;

import java.util.Set;
import java.util.UUID;

/**
 * Goi xuong tang xac thuc phien thật (Spring Session JDBC) de huy phien.
 *
 * <p>Khong duoc phu thuoc truc tiep vao {@code SpringSessionBackedSessionRepository}:
 * domain/application khong duoc phai biet Spring Session (R2, R3, R7). Trien khai
 * o {@code ..infrastructure.session}.</p>
 */
public interface AuthenticatedSessionRevoker {

    /** Huy cac phien dang con cua mot tai khoan. Tra ve so phien bi huy. */
    int revokeAllByUserId(UUID userId);

    /** Huy dung mot phien theo {@code sessionId} cua Spring Session. */
    void revokeBySessionId(String sessionId);

    /** Lay danh sach {@code sessionId} dang con cua mot tai khoan. */
    Set<String> activeSessionIdsByUserId(UUID userId);
}
