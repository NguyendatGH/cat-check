package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.ConsentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ghi/đọc bằng chứng {@code consent_record} — bảng append-only (bất biến I16).
 *
 * <p>Cổng này <b>không có</b> phương thức update/delete: bảng bị REVOKE UPDATE, DELETE
 * ở tầng DB (V6__privacy.sql). Rút đồng ý = {@link #append} dòng mới {@code WITHDRAWN}
 * + {@code supersedes_id} (p15 §15.3.5).</p>
 */
public interface ConsentRecordPort {

    /** INSERT một dòng bằng chứng mới. Chỉ INSERT — không đường nào khác được phép sửa bảng. */
    void append(ConsentRecord record);

    /**
     * Dòng mới nhất của cặp (user, purpose) — đọc qua view {@code consent_current}
     * (DISTINCT ON, p4 B2). Trả về rỗng nếu user chưa từng được hỏi về mục đích này.
     */
    Optional<ConsentRecord> findLatest(UUID userId, String purposeCode);

    /**
     * Trạng thái hiện hành của một mục đích, null khi chưa từng ghi. Dùng kiểm tra
     * consent gate (CONSENT_REQUIRED) và ghi activity log (chỉ khi PRODUCT_ANALYTICS).
     */
    ConsentStatus findCurrentStatus(UUID userId, String purposeCode);

    /** Toàn bộ dòng bằng chứng của user, mới nhất trước — lịch sử tuân thủ (C4). */
    List<ConsentRecord> findHistoryByUser(UUID userId, Instant before, int limit);

    /** Một dòng bằng chứng theo id — dùng nối {@code supersedes_id} khi tái dựng màn hình. */
    Optional<ConsentRecord> findById(UUID id);
}
