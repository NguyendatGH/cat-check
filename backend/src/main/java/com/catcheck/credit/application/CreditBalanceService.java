package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBalance;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Số dư credit — {@code GET /api/v1/credits/balance} (p8 H2, p5 R4).
 *
 * <p>Read-only, không khoá dòng nào. Số dư có thể lệch vài mili giây so với một lần trừ đang
 * chạy — chấp nhận được, vì lần xác nhận tiêu credit luôn là lần ghi ledger trong transaction
 * của scan, không phải lần đọc màn hình này.</p>
 */
@Service
public class CreditBalanceService {

    private final CreditBatchPort creditBatchPort;
    private final UserEntitlementPort userEntitlementPort;
    private final Clock clock;

    public CreditBalanceService(
            CreditBatchPort creditBatchPort,
            UserEntitlementPort userEntitlementPort,
            Clock clock
    ) {
        this.creditBatchPort = creditBatchPort;
        this.userEntitlementPort = userEntitlementPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CreditBalance balanceOf(UUID userId) {
        Instant now = clock.instant();
        int trialScansUsed = userEntitlementPort.findOrDefault(userId, now).trialScansUsed();

        List<CreditBalance.BatchAvailability> batches = creditBatchPort.findLiveBatches(userId, now).stream()
                .map(batch -> new CreditBalance.BatchAvailability(
                        batch.id(),
                        batch.packageCode(),
                        batch.initialAmount(),
                        batch.remainingAmount(),
                        batch.activatedAt(),
                        batch.expiresAt(),
                        remainingUntil(batch.expiresAt(), now)))
                .toList();

        int total = batches.stream().mapToInt(CreditBalance.BatchAvailability::remainingAmount).sum();

        return new CreditBalance(
                total,
                trialScansUsed,
                Math.max(0, FefoCreditConsumptionService.TRIAL_SCAN_LIMIT - trialScansUsed),
                batches);
    }

    /**
     * Thời gian còn lại tới hạn, dạng {@link Duration} để client tự quyết định hiển thị "còn 5 ngày"
     * hay "còn 3 giờ" — server không chọn hộ thang đo thời gian cho client (p5 R4 chỉ yêu cầu
     * hiện được mốc thời gian của từng lô).
     */
    private Duration remainingUntil(Instant expiresAt, Instant now) {
        Duration remaining = Duration.between(now, expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
