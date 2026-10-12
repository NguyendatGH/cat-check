package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditLedgerQueryPort {

    record LedgerPage(List<LedgerRow> entries, boolean hasMore, LedgerCursor nextCursor) {

        public LedgerPage {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    record LedgerRow(
            UUID id,
            CreditLedgerType type,
            int amount,
            int balanceAfter,
            UUID batchId,
            String packageCode,
            Instant expiresAt,
            CreditLedgerRefType refType,
            String note,
            Instant createdAt
    ) {
    }

    record LedgerCursor(Instant sortKey, UUID id) {

        public LedgerCursor {
            if (sortKey == null || id == null) {
                throw new IllegalArgumentException("ledgerCursor thiếu trường bắt buộc");
            }
        }
    }

    LedgerPage findByUser(UUID userId, LedgerCursor cursor, int limit);

    LedgerOffsetPage findByUserForAdmin(UUID userId, int offset, int limit);

    record LedgerOffsetPage(List<LedgerRow> entries, long totalElements) {

        public LedgerOffsetPage {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
