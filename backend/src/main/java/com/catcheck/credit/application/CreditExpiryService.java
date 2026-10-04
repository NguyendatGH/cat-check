package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Đóng một lô credit đã hết hạn — nghiệp vụ của {@code ExpireCreditBatchesJob} (p5 R3,
 * p12 §12.6.2), tách khỏi lớp job để job chỉ còn lo lịch, khoá và nhật ký.
 *
 * <p>p5 R3 chốt đúng ba việc, phải xảy ra cùng nhau hoặc không xảy ra: ghi
 * {@code credit_ledger(EXPIRE)} với số đúng bằng phần còn lại, đặt {@code remaining_amount = 0},
 * đặt {@code status = EXPIRED}. Dòng ledger là thứ giữ bất biến I1
 * ({@code initial + SUM(ledger) = remaining}) — đưa {@code remaining} về 0 mà không ghi ledger
 * sẽ làm sổ cái lệch vĩnh viễn, và đó là loại lỗi không sửa được về sau vì
 * {@code credit_ledger} là append-only (p4 §4.6.2).</p>
 *
 * <p><b>{@code Propagation.REQUIRES_NEW} cho từng lô</b> là yêu cầu tường minh của p12 §12.6.2:
 * <i>"Mỗi batch một transaction riêng; lỗi 1 batch không chặn batch khác"</i>. Một transaction
 * chung cho cả lần chạy sẽ biến một lô lỗi thành "cả 500 lô không hết hạn", và giờ sau job lại
 * gặp đúng lô lỗi đó.</p>
 */
@Service
public class CreditExpiryService {

    private final CreditLedgerPort creditLedgerPort;
    private final CreditBatchPort creditBatchPort;
    private final UuidV7 uuidV7;

    public CreditExpiryService(
            CreditLedgerPort creditLedgerPort,
            CreditBatchPort creditBatchPort,
            UuidV7 uuidV7
    ) {
        this.creditLedgerPort = creditLedgerPort;
        this.creditBatchPort = creditBatchPort;
        this.uuidV7 = uuidV7;
    }

    /** Id các lô đủ điều kiện đóng, nhiều nhất {@code limit} dòng (chỉ đọc, không khoá). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<UUID> findDueBatchIds(Instant now, int limit) {
        return creditBatchPort.findDueForExpiry(now, limit);
    }

    /** Số lô đủ điều kiện đóng — dùng cho lần chạy {@code dry_run}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int countDueBatches(Instant now) {
        return creditBatchPort.countDueForExpiry(now);
    }

    /**
     * Đóng đúng một lô, trong transaction riêng.
     *
     * @param batchId  lô cần đóng
     * @param jobRunId {@code job_run.id} của lần chạy — ghi vào {@code credit_ledger.ref_id} vì
     *                 {@code ref_type = 'JOB'} được định nghĩa là trỏ tới {@code job_run.id}
     *                 (p4 §4.4.6, enum {@link CreditLedgerRefType#JOB}). Nhờ đó mỗi dòng
     *                 {@code EXPIRE} truy ngược được về lần chạy đã sinh ra nó.
     * @param now      mốc thời gian của lần chạy, lấy từ {@code Clock} đã inject (R13)
     * @return {@code true} nếu lô vừa bị đóng; {@code false} nếu lô không còn đủ điều kiện
     *         (instance khác đã đóng, hoặc dòng đang bị khoá — {@code SKIP LOCKED} bỏ qua)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean expireBatch(UUID batchId, UUID jobRunId, Instant now) {
        Optional<ExpiringCreditBatch> locked = creditLedgerPort.lockBatchForExpiry(batchId, now);
        if (locked.isEmpty()) {
            // Idempotency của p12 §12.6.2: lần chạy thứ hai không tìm thấy lô nào khớp bộ lọc
            // nên không ghi ledger trùng. KHÔNG phải lỗi — không đếm vào items_failed.
            return false;
        }

        ExpiringCreditBatch batch = locked.get();
        creditLedgerPort.markExpired(batch.id());

        // Số dư đọc SAU khi lô đã về 0: balance_after phải phản ánh trạng thái sau giao dịch
        // (p5 R4), và lô vừa đóng không còn được tính vào số dư khả dụng.
        int balanceAfter = creditLedgerPort.availableBalance(batch.userId(), now);

        creditLedgerPort.append(new LedgerEntry(
                uuidV7.generate(),
                batch.userId(),
                batch.id(),
                CreditLedgerType.EXPIRE,
                // Âm, đúng bằng phần chưa dùng (p5 R3). ck_credit_ledger_sign chặn dấu sai ở DB.
                -batch.remainingAmount(),
                balanceAfter,
                CreditLedgerRefType.JOB,
                jobRunId,
                // idempotency_key để NULL: p5 R8 dành khoá đó cho request của người dùng, và
                // UNIQUE một cột sẽ biến "hai lô hết hạn trong cùng lần chạy" thành lỗi ràng buộc.
                null,
                null,
                now));
        return true;
    }
}
