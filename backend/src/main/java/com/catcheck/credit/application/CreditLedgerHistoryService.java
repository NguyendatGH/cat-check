package com.catcheck.credit.application;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerCursor;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreditLedgerHistoryService {

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private final CreditLedgerQueryPort creditLedgerQueryPort;

    public CreditLedgerHistoryService(CreditLedgerQueryPort creditLedgerQueryPort) {
        this.creditLedgerQueryPort = creditLedgerQueryPort;
    }

    @Transactional(readOnly = true)
    public LedgerPage page(UUID userId, LedgerCursor cursor, Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        return creditLedgerQueryPort.findByUser(userId, cursor, effectiveLimit);
    }
}
