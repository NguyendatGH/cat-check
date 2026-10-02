package com.catcheck.credit.application;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerCursor;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lịch sử giao dịch credit — {@code GET /api/v1/credits/ledger} (p8 H3).
 *
 * <p>Read-only, phân trang keyset theo {@code (created_at, id)} xuống dần. Không có
 * {@code @Transactional} cần thiết: một câu đọc đơn lẻ, đọc ở READ COMMITTED là đủ.</p>
 */
@Service
public class CreditLedgerHistoryService {

    /** Số dòng mặc định của một trang (p8 §8.1.4). */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Trần cứng — chặn client đòi 10.000 dòng một lượt (p8 §8.1.4). */
    public static final int MAX_PAGE_SIZE = 100;

    private final CreditLedgerQueryPort creditLedgerQueryPort;

    public CreditLedgerHistoryService(CreditLedgerQueryPort creditLedgerQueryPort) {
        this.creditLedgerQueryPort = creditLedgerQueryPort;
    }

    /**
     * Một trang sổ cái.
     *
     * @param userId chủ sổ — mọi truy vấn đều lọc theo user này, không có ngoại lệ (bất biến I14:
     *               không bao giờ trả dòng của người khác)
     * @param cursor khoá trang trước, {@code null} = trang đầu
     * @param limit  kích thước trang; {@code null} = {@value #DEFAULT_PAGE_SIZE}
     */
    @Transactional(readOnly = true)
    public LedgerPage page(UUID userId, LedgerCursor cursor, Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        return creditLedgerQueryPort.findByUser(userId, cursor, effectiveLimit);
    }
}
