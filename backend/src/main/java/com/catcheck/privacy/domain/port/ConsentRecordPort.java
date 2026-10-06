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
    default List<ConsentRecord> findHistoryByUser(UUID userId, Instant before, int limit) {
        return findHistoryByUser(userId, before, null, limit);
    }

    /**
     * Như trên nhưng keyset theo <b>cặp</b> {@code (occurred_at, id)} — dùng cho L55
     * ({@code GET /admin/privacy/consents}, phân trang cursor).
     *
     * <p><b>Vì sao phải có {@code beforeId}, phát hiện bằng curl trên dữ liệu thật:</b> một
     * lần ghi consent sinh <b>nhiều dòng trong cùng transaction</b> (mỗi mục đích một dòng,
     * p15 §15.3.1) nên chúng có {@code occurred_at} <b>giống hệt nhau tới microsecond</b>. Chỉ
     * lọc {@code occurred_at < before} thì trang sau nhảy qua toàn bộ phần còn lại của nhóm:
     * đo thật trên user có 4 dòng cùng mốc, {@code limit=2} trả 2 dòng rồi trang 2 trả
     * <b>0 dòng</b> — hai dòng bằng chứng pháp lý biến mất khỏi màn hình của DPO. Đây đúng lý
     * do {@code DsarRequestPort.findByUser} đã dùng keyset cặp từ đầu.</p>
     *
     * @param beforeId {@code id} của dòng cuối trang trước; {@code null} = trang đầu
     */
    List<ConsentRecord> findHistoryByUser(UUID userId, Instant before, UUID beforeId, int limit);

    /** Một dòng bằng chứng theo id — dùng nối {@code supersedes_id} khi tái dựng màn hình. */
    Optional<ConsentRecord> findById(UUID id);
}
