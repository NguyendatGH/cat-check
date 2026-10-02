package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.LedgerEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ghi vào sổ cái {@code credit_ledger} + cập nhật số dư lô.
 *
 * <p>Tách riêng khỏi cổng đọc vì {@link #lockLiveBatchesForFefo} là thao tác ghi: nó khoá dòng
 * thật trong DB ({@code SELECT ... FOR UPDATE}). Ranh giới transaction thuộc tầng
 * {@code application} (p7 §7.3.2), nên mọi phương thức ở đây đều chạy trong transaction của
 * use case gọi nó — không phương thức nào tự mở hay tự commit.</p>
 */
public interface CreditLedgerPort {

    /**
     * <b>Đường FEFO — hàm quan trọng nhất của module credit.</b>
     *
     * <p>Khoá và trả về các lô còn hiệu lực của user, <b>sắp xếp theo {@code expires_at} tăng
     * dần</b> (rồi tới {@code id} để thứ tự tuyệt đối xác định). Thứ tự sắp xếp này vừa quyết
     * định lô nào bị trừ trước (FEFO — p5 R2), vừa là thứ tự khoá cố định giữa các transaction
     * nên hai request trùng lô không kẹt nhau (p5 §5.6).</p>
     *
     * <p>Bộ lọc {@code status='ACTIVE' AND expires_at > now AND remaining_amount > 0} là
     * bất biến I3: lô {@code EXPIRED}, hoặc lô đã quá hạn mà job chưa chạy tới, KHÔNG bao giờ
     * nằm trong tập ứng viên.</p>
     *
     * <p>Chỉ trả về lô còn credit — lô {@code remaining_amount = 0} không cần khoá vì không
     * thể trừ thêm; {@code CHECK (remaining_amount >= 0)} chặn kết quả âm ở tầng DB.</p>
     */
    List<CreditBatchSnapshot> lockLiveBatchesForFefo(UUID userId, Instant now);

    /**
     * Khoá <b>đúng một lô</b> theo khoá chính, bất kể trạng thái — dành riêng cho hoàn credit
     * (p5 R7).
     *
     * <p>Không dùng lại {@link #lockLiveBatchesForFefo} cho việc này: hàm đó lọc
     * {@code remaining_amount > 0}, mà lý do phổ biến nhất để hoàn là chính lô đó đã cạn hoặc đã
     * quá hạn. Lọc như vậy khiến hoàn thất bại đúng lúc cần nhất. Ở đây cũng chỉ khoá một dòng
     * thay vì khoá cả danh sách, nên hoàn không tranh chấp với một lần trừ đang chạy.</p>
     *
     * <p>Hoàn vào lô đã quá hạn vẫn được phép và là đúng: số được trả về đúng chỗ đã lấy ra nên
     * bất biến I1 ({@code initial + SUM(ledger) = remaining}) vẫn đúng; số đó chỉ đơn giản là không
     * dùng được nữa vì lô đã hết hạn. Từ chối hoàn sẽ để lại một dòng {@code CONSUME} không
     * khớp, khó truy vết hơn là hoàn rồi không dùng được.</p>
     */
    Optional<CreditBatchSnapshot> lockBatchForRefund(UUID batchId);

    /**
     * Ghi số dư còn lại mới của một lô. Implement <b>phải</b> chỉ cập nhật dòng vừa được
     * {@link #lockLiveBatchesForFefo} khoá.
     */
    void updateRemaining(UUID batchId, int remainingAmount);

    /** Đóng lô khi {@code remainingAmount} chạm 0 (p5 R3 — không xoá dòng). */
    void markExhausted(UUID batchId);

    /**
     * Mở lại lô vừa được hoàn credit vào. Đối xứng với {@link #markExhausted}: nếu lô đã cạn
     * rồi được cộng trở lại thì nó phải trở lại tập ứng viên của FEFO, nếu không số vừa hoàn sẽ
     * nằm im trong DB mà không bao giờ dùng được.
     *
     * <p>Chỉ gỡ cờ {@code status = 'EXHAUSTED'}. {@code credit_batch} không có cột cờ "đã nhắc
     * hết credit" (p4 §4.5.1 chốt đúng 12 cột, không có) — chống gửi lặp thuộc về
     * {@code notification.dedupe_key} UNIQUE, không phải credit.</p>
     */
    void markActive(UUID batchId);

    /**
     * Đóng lô do hết hạn: {@code remaining_amount = 0} + {@code status = 'EXPIRED'} (p5 R3).
     *
     * <p>KHÔNG ghi kèm dòng ledger {@code EXPIRE} ở đây: việc ghi dòng thuộc use case gọi
     * ({@code ExpireCreditBatchesJob}, p12 §12.6) để mỗi phương thức chỉ làm đúng một việc, và để
     * adapter không giấu một INSERT bên trong một UPDATE.</p>
     */
    void markExpired(UUID batchId);

    /**
     * Đánh dấu đã gửi nhắc T-48h. Chỉ là bộ lọc nhanh để job quét mỗi giờ không gửi lại; chống
     * gửi trùng thật sự là {@code notification.dedupe_key} UNIQUE ở module notification, không
     * phải cột này (p12 §12.6).
     */
    void markT48hNotified(UUID batchId);

    /** Đánh dấu đã gửi nhắc T-6h — xem {@link #markT48hNotified}. */
    void markT6hNotified(UUID batchId);

    /**
     * Ghi một dòng sổ cái. Bảng là append-only: cổng này <b>không có</b> phương thức update hay
     * delete (p4 §4.6.2 chặn bằng quyền DB, không phải bằng kỷ luật).
     */
    void append(LedgerEntry entry);

    /**
     * Số dư khả dụng toàn user = {@code SUM(remaining_amount)} của các lô
     * {@code status='ACTIVE' AND expires_at > now} (p5 R4). Chạy trong cùng transaction với
     * lần ghi ledger để {@code balance_after} luôn phản ánh trạng thái SAU giao dịch.
     */
    int availableBalance(UUID userId, Instant now);

    /**
     * Tổng {@code amount} của mọi dòng ledger thuộc một lô. Dùng để kiểm bất biến I1
     * ({@code initial_amount + SUM(ledger.amount) = remaining_amount}).
     */
    int sumLedgerAmountForBatch(UUID batchId);

    /** Đọc một dòng ledger theo id — cần cho {@code refund} (p5 R7: hoàn về đúng batch đã trừ). */
    Optional<LedgerEntryRef> findById(UUID ledgerEntryId);

    /**
     * Tra dòng ledger đã ghi với {@code idempotency_key} này, phục vụ replay (p5 R8, bất biến
     * I4: một {@code idempotency_key} chỉ sinh MỘT dòng ledger).
     *
     * <p>Đây là cách xử lý double-submit <b>không dựa vào ngoại lệ DB</b>: kiểm tra trước rồi mới
     * ghi, thay vì để {@code UNIQUE} nổ lên thành 500.</p>
     *
     * @return dòng đã ghi trước đó, hoặc rỗng nếu đây là lần gọi đầu
     */
    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    /**
     * Mọi dòng {@code CONSUME} sinh ra từ MỘT lần trừ, xác định bằng
     * {@code (ref_type, ref_id)} — tức là đúng một lần scan.
     *
     * <p>Cần cho <b>replay</b>: khoá idempotency là UNIQUE một cột (p4 §4.6.1 dòng 2313) nên
     * chỉ dòng ĐẦU mang khoá, các dòng sau của lần trừ vắt qua nhiều lô phải để NULL. Đọc bằng
     * {@link #findByIdempotencyKey} thì chỉ dựng lại được phần đầu, và {@code charged} sẽ báo
     * đúng bằng số của lô đầu thay vì tổng đã trừ — sai số liệu cho người dùng.</p>
     *
     * <p>Một lần trừ chỉ gắn đúng một {@code ref_id} (id scan), nên không lẫn với lần trừ khác.</p>
     */
    List<LedgerEntry> findConsumeRowsByRef(CreditLedgerRefType refType, UUID refId);

    /**
     * Dòng {@code CONSUME} này đã được hoàn chưa.
     *
     * <p>{@code UNIQUE (idempotency_key)} chỉ chặn hoàn <i>trùng khoá</i>, không chặn hoàn hai
     * lần bằng hai khoá khác nhau. Mà p5 R7 chỉ có đúng một khoản hoàn cho mỗi lần trừ: hoàn lần
     * nữa là tiền phát sinh từ hư không.</p>
     */
    boolean existsRefundReferencing(UUID consumedLedgerEntryId);

    /**
     * Một dòng ledger rút gọn kèm các trường cần cho refund.
     *
     * @param id      id dòng ledger
     * @param type    loại dòng — refund chỉ chấp nhận {@code CONSUME} hoặc {@code EXPIRE}
     * @param batchId lô đã bị trừ, bắt buộc có
     * @param amount  số đã trừ (âm)
     * @param userId  chủ credit
     */
    record LedgerEntryRef(UUID id, CreditLedgerType type, UUID batchId, UUID userId, int amount) {

        public LedgerEntryRef {
            if (id == null || batchId == null || userId == null) {
                throw new IllegalArgumentException("ledgerEntryRef thiếu trường bắt buộc");
            }
            if (type == null) {
                throw new IllegalArgumentException("ledgerEntryRef thiếu type");
            }
            if (amount >= 0) {
                throw new IllegalArgumentException("Chỉ hoàn được dòng CONSUME/EXPIRE (amount âm): " + amount);
            }
        }

        /**
         * Dòng này có thể hoàn được không. {@code GRANT} không hoàn được (mất mã kích hoạt thì
         * mất gói, không phải mất tiền do tiêu sai), {@code REFUND} không hoàn được (sẽ thành
         * vòng lặp cộng/trừ vô hạn nếu hoàn hai lần).
         */
        public boolean isRefundable() {
            return type == CreditLedgerType.CONSUME || type == CreditLedgerType.EXPIRE;
        }
    }
}
