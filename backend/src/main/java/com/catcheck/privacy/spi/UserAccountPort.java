package com.catcheck.privacy.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Cổng đọc/ghi trạng thái tài khoản — <b>adapter do module identity (A1) implement</b>,
 * vì {@code app_user} là của identity. Module privacy không được import class identity
 * trực tiếp (ràng buộc ORCHESTRATOR + R6).
 *
 * <p>Implement kỳ vọng (ghi rõ trong {@code docs/handovers/A2.md}):
 * <ul>
 *   <li>{@link #snapshot(UUID)} — đọc ảnh chụp tài khoản (trạng thái, processing_restricted_at,
 *       deletion_scheduled_at, anonymized_at, pseudonym_id, roles, email).</li>
 *   <li>{@link #requestAccountDeletion(UUID, Instant)} — đổi {@code status = DELETION_REQUESTED}
 *       + ghi {@code deletion_scheduled_at}; thu hồi toàn bộ phiên là việc của identity.</li>
 *   <li>{@link #cancelAccountDeletion(UUID)} — {@code status = ACTIVE}, xoá
 *       {@code deletion_scheduled_at}.</li>
 *   <li>{@link #setProcessingRestricted(UUID)} — {@code status = RESTRICTED} +
 *       {@code processing_restricted_at = now} (p8 C11, p15 §15.4.7).</li>
 *   <li>{@link #liftProcessingRestriction(UUID)} — {@code status = ACTIVE}, xoá
 *       {@code processing_restricted_at}.</li>
 * </ul>
 * Không có bean implement nào ở M1 → mọi use case phụ thuộc cổng này phải fail-closed
 * rõ ràng; adapter do A1 bổ sung (xem handoff).</p>
 */
public interface UserAccountPort {

    UserAccountSnapshot snapshot(UUID userId);

    /** Yêu cầu xoá tài khoản: chuyển DELETION_REQUESTED + chốt mốc thực thi (D+7, TD-05). */
    void requestAccountDeletion(UUID userId, Instant scheduledAt);

    /** Huỷ yêu cầu xoá trong ân hạn 7 ngày (C10 — cũng gọi được từ link email). */
    void cancelAccountDeletion(UUID userId);

    /** Bật hạn chế xử lý (C11): dữ liệu GIỮ NGUYÊN, hệ thống chỉ lưu trữ (p15 §15.4.7). */
    void setProcessingRestricted(UUID userId);

    /** Rút yêu cầu hạn chế (C12): về ACTIVE. */
    void liftProcessingRestriction(UUID userId);
}
