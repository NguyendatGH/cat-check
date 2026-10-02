package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code credit_batch} — màn số dư, màn chi tiết lô, và tạo lô mới khi kích hoạt.
 *
 * <p>{@link #insertNewBatch(CreditBatchGrant)} chạy trong transaction của use case gọi (ranh giới
 * transaction thuộc tầng {@code application}, p7 §7.3.2). Các phương thức đọc không khoá dòng
 * nào — chỉ {@link CreditLedgerPort#lockLiveBatchesForFefo} khoá, vì chỉ đường ghi mới cần.</p>
 */
public interface CreditBatchPort {

    /**
     * Tạo một lô credit mới từ một lần kích hoạt gói. Phải đi kèm dòng ledger
     * {@code GRANT} trong CÙNG transaction, không thì bất biến I1 vi phạm.
     */
    void insertNewBatch(CreditBatchGrant batch);

    /**
     * Các lô CÒN HIỆU LỰC của user, sắp theo {@code expires_at} tăng dần (FEFO) — cùng thứ tự
     * với đường ghi để người dùng nhìn thấy đúng thứ tự tiêu credit sẽ diễn ra (p5 R4/R5.8).
     */
    List<CreditBatchView> findLiveBatches(UUID userId, Instant now);

    /** Tất cả lô của user kể cả đã đóng — dùng cho đối soát, không dùng cho hiển thị. */
    List<CreditBatchView> findAllBatches(UUID userId);
}
