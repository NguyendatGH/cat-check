package com.catcheck.credit.api;

import com.catcheck.credit.domain.CreditLedgerRefType;

import java.util.List;
import java.util.UUID;

/**
 * <b>HỢP ĐỒNG MÀ MODULE {@code scan} PHẢI GỌI — học từ p7 §7.4.5.</b>
 *
 * <p>Đây là toàn bộ bề mặt module credit dành cho việc TRỪ credit. Ba quy tắc bắt buộc, theo
 * đúng thứ tự thẩm quyền:</p>
 *
 * <ol>
 *   <li><b>Phải gọi bên trong transaction của module gọi.</b> p7 §7.4.5 (TD-02): luồng scan là
 *       MỘT BƯỚC — trừ credit ĐỒNG BỘ, TRONG CÙNG TRANSACTION với {@code INSERT scan} +
 *       {@code scan_analysis}. Lý do: bất biến I1 ({@code initial_amount + SUM(ledger.amount)
 *       = remaining_amount}) chỉ giữ được trong một transaction; trừ qua event async thì user
 *       spam được scan miễn phí, còn listener chết là mất doanh thu và ledger lệch.
 *       Hiện thực dùng {@code Propagation.MANDATORY} nên gọi ngoài transaction sẽ ném
 *       {@link org.springframework.transaction.IllegalTransactionStateException} chứ không
 *       âm thầm mở transaction mới — đó là cố ý, không phải lỗi cấu hình.</li>
 *   <li><b>Chỉ gọi SAU khi đã có kết quả phân tích hợp lệ.</b> p5 R7: "phân tích ảnh → có kết
 *       quả hợp lệ → mới trừ credit và lưu. Không trừ trước." Kết quả
 *       {@code INCONCLUSIVE} trả 200 và KHÔNG gọi {@link #consume}.</li>
 *   <li><b>Mọi thao tác trừ credit đi qua FEFO + {@code SELECT ... FOR UPDATE}</b> — luật cứng
 *       số 9 trong {@code CLAUDE.md} và p5 §5.6. Bên gọi không được tự trừ bằng một câu UPDATE
 *       riêng: làm vậy là phá vỡ bất biến I1.</li>
 * </ol>
 *
 * <p>Module gọi phải khai báo {@code allowedDependencies = {..., "credit::api"}} trong
 * {@code package-info.java} của mình (xem {@code docs/handovers/A4.md}).</p>
 *
 * <p>Các lỗi ném ra (xử lý chung bởi {@code GlobalExceptionHandler} của shared):</p>
 * <ul>
 *   <li>{@link CreditErrorCode#CREDIT_INSUFFICIENT} (402) — không đủ credit lô VÀ hết trial.</li>
 *   <li>{@link CreditErrorCode#WRITE_ACCESS_EXPIRED} (403) — còn credit nhưng quyền tạo mới đã
 *       hết hạn (p5 R5).</li>
 * </ul>
 */
public interface CreditConsumption {

    /**
     * Trừ credit theo FEFO và ghi sổ cái, trong transaction của bên gọi.
     *
     * <p>Ghi <b>một dòng {@code credit_ledger} cho mỗi lô bị trừ} (p5 R2) — {@code requestedCredits > 1}
     * có thể vắt qua nhiều lô, kết quả trả về liệt kê từng dòng.</p>
     *
     * @param userId  chủ credit
     * @param command lệnh trừ
     * @return kết quả, chỉ hợp lệ sau khi transaction của bên gọi commit
     * @throws com.catcheck.shared.error.BusinessRuleException {@link CreditErrorCode#CREDIT_INSUFFICIENT}
     *         khi tổng khả dụng nhỏ hơn {@code command.credits()}
     * @throws com.catcheck.shared.error.PermissionDeniedException {@link CreditErrorCode#WRITE_ACCESS_EXPIRED}
     *         khi còn credit nhưng {@code user_entitlement.write_access_until} đã qua
     */
    CreditCharge consume(UUID userId, CreditConsumeCommand command);

    /**
     * Hoàn credit về <b>đúng lô đã trừ</b> (p5 R7). Dùng khi scan đã trừ xong nhưng phát hiện
     * lỗi hệ thống ở bước sau (pipeline lỗi, timeout, lỗi DB khi persist).
     *
     * <p>Không bao giờ UPDATE dòng ledger cũ — luôn ghi một dòng {@code REFUND} mới trỏ cùng
     * {@code batch_id} (sổ cái append-only, p4 §4.6.2). Ảnh không đạt chất lượng thì KHÔNG gọi
     * hàm này, vì vốn dĩ đã không trừ.</p>
     *
     * @param userId  chủ credit
     * @param command lệnh hoàn
     * @throws com.catcheck.shared.error.BusinessRuleException khi không tìm thấy dòng ledger
     *         gốc, hoặc dòng đó không phải {@code CONSUME}/{@code EXPIRE}
     */
    CreditRefund refund(UUID userId, CreditRefundCommand command);

    /**
     * Đọc-only: còn credit khả dụng hay không. <b>Không khoá dòng nào</b> — chỉ để chặn sớm
     * trước khi tốn CPU phân tích ảnh (p7 §7.4.5: {@code App->>Ent: canScan(userId)}).
     */
    boolean hasAvailableCredit(UUID userId);

    /**
     * Đọc-only: còn lượt trial không (p5 R6 — 3 lượt, tính theo tài khoản, không theo lô, không
     * hết hạn). Không khoá dòng nào.
     */
    boolean hasTrialScanRemaining(UUID userId);

    /**
     * Tiêu một lượt trial: tăng {@code user_entitlement.trial_scans_used} đúng một lần.
     *
     * <p>KHÔNG đụng {@code credit_batch} nào và KHÔNG ghi dòng ledger nào (p5 R6, p17 C10) —
     * bất biến I5: {@code scan.is_trial = true} ⇒ {@code credit_ledger_id IS NULL}.</p>
     *
     * <p>Gọi trong transaction của bên gọi, cùng chỗ với {@code INSERT scan}.</p>
     *
     * @return {@code true} nếu tiêu thành công; {@code false} nghĩa là đã hết lượt trial
     */
    boolean consumeTrialScan(UUID userId);

    /**
     * Lệnh trừ credit.
     *
     * @param credits         số credit cần trừ, phải {@code >= 1}
     * @param idempotencyKey  khoá chống double-submit, {@code <= 64} ký tự; bắt buộc vì
     *                        {@code credit_ledger.idempotency_key} là UNIQUE (p5 R8, bất biến I4).
     *                        Client retry cùng key ⇒ server trả kết quả cũ, không trừ lần hai.
     * @param refType         loại tài nguyên tham chiếu, {@link CreditLedgerRefType#SCAN} cho scan
     * @param refId           id tài nguyên tham chiếu (không phải FK — p4 §4.9.3)
     * @param note            ghi chú tùy chọn
     */
    record CreditConsumeCommand(
            int credits,
            String idempotencyKey,
            CreditLedgerRefType refType,
            UUID refId,
            String note
    ) {

        public CreditConsumeCommand {
            if (credits < 1) {
                throw new IllegalArgumentException("credits phải >= 1: " + credits);
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey là bắt buộc (p5 R8)");
            }
        }
    }

    /**
     * Lệnh hoàn credit.
     *
     * @param consumedLedgerEntryId dòng ledger {@code CONSUME} cần hoàn
     * @param idempotencyKey        khoá chống hoàn hai lần cho cùng một dòng gốc
     * @param note                  ghi chú tùy chọn
     */
    record CreditRefundCommand(
            UUID consumedLedgerEntryId,
            String idempotencyKey,
            String note
    ) {

        public CreditRefundCommand {
            if (consumedLedgerEntryId == null) {
                throw new IllegalArgumentException("consumedLedgerEntryId là bắt buộc");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey là bắt buộc (p5 R8)");
            }
        }
    }

    /**
     * Kết quả trừ credit.
     *
     * @param chargedCredits tổng số credit đã trừ
     * @param balanceAfter   số dư khả dụng toàn user sau giao dịch (khớp {@code credit_ledger.balance_after})
     * @param ledgerEntries  một {@code ChargedBatch} cho MỖI lô bị trừ
     */
    record CreditCharge(
            int chargedCredits,
            int balanceAfter,
            List<ChargedBatch> ledgerEntries
    ) {

        public CreditCharge {
            ledgerEntries = ledgerEntries == null ? List.of() : List.copyOf(ledgerEntries);
        }
    }

    /**
     * Một lô đã bị trừ.
     *
     * @param batchId   lô bị trừ — {@code scan.credit_ledger_id} trỏ tới dòng ledger này
     * @param ledgerId  id dòng {@code credit_ledger} vừa ghi, dùng để hoàn (p5 R7)
     * @param amount    số credit đã trừ từ lô này (dương, xem {@code amount} trong ledger là âm)
     */
    record ChargedBatch(UUID batchId, UUID ledgerId, int amount) {

        public ChargedBatch {
            if (batchId == null || ledgerId == null || amount < 1) {
                throw new IllegalArgumentException("chargedBatch không hợp lệ");
            }
        }
    }

    /**
     * Kết quả hoàn credit.
     *
     * @param batchId      lô được hoàn lại
     * @param refundId     id dòng {@code credit_ledger} loại {@code REFUND} vừa ghi
     * @param refunded     số credit đã hoàn
     * @param balanceAfter số dư khả dụng toàn user sau giao dịch
     */
    record CreditRefund(
            UUID batchId,
            UUID refundId,
            int refunded,
            int balanceAfter
    ) {
    }
}
