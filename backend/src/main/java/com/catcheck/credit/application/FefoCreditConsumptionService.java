package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>Tim tròng của module credit: tiêu credit theo FEFO (First Expiring, First Out).</b>
 *
 * <p>Đây là hiện thực {@code credit.api.CreditConsumption} — hợp đồng mà module
 * {@code scan} gọi từ trong transaction của chính nó (p7 §7.4.5 / TD-02).</p>
 *
 * <p>Thuật toán, theo đúng p5 R2 + §5.6:</p>
 * <ol>
 *   <li>{@code SELECT ... FOR UPDATE} trên các lô {@code status='ACTIVE' AND expires_at > now
 *       AND remaining_amount > 0}, <b>ORDER BY expires_at</b> (rồi {@code id} để tuyệt đối xác định).
 *       Thứ tự sắp xếp vừa chọn lô bị trừ trước, vừa là thứ tự khoá cố định giữa các
 *       transaction nên hai request trùng lô không kẹt nhau.</li>
 *   <li>Trừ từ đầu danh sách (lô sắp hết hạn nhất), vắt sang lô kế tiếp khi lô trước không đủ
 *       (p17 C3).</li>
 *   <li>Ghi <b>một dòng ledger cho mỗi lô bị trừ</b>, cùng transaction.</li>
 *   <li>Tính {@code balance_after} SAU khi cập nhật, ghi vào mọi dòng của lần giao dịch đó.</li>
 * </ol>
 *
 * <p>Ba lớp phòng thủ chồng nhau (p5 §5.6, p4 §4.5.1): khoá dòng ở tầng DB,
 * {@code CHECK (remaining_amount >= 0 AND remaining_amount <= initial_amount)} ở tầng DB, và
 * {@code UNIQUE (idempotency_key)} trên ledger cho double-submit.</p>
 */
@Service
public class FefoCreditConsumptionService implements com.catcheck.credit.api.CreditConsumption {

    /**
     * Số lượt trial miễn phí cho tài khoản chưa kích hoạt gói nào (p5 R6: "3 lần trial scan
     * miễn phí, tính theo tài khoản, không theo lô, không hết hạn").
     */
    public static final int TRIAL_SCAN_LIMIT = 3;

    private static final Logger log = LoggerFactory.getLogger(FefoCreditConsumptionService.class);

    private final CreditLedgerPort creditLedgerPort;
    private final UserEntitlementPort userEntitlementPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public FefoCreditConsumptionService(
            CreditLedgerPort creditLedgerPort,
            UserEntitlementPort userEntitlementPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.creditLedgerPort = creditLedgerPort;
        this.userEntitlementPort = userEntitlementPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Propagation = MANDATORY là cố ý.</b> Nếu để mặc định ({@code REQUIRED}) mà ai đó gọi
     * ngoài transaction, hàm sẽ tự mở transaction riêng và trừ credit thành công trong khi
     * {@code INSERT scan} ở transaction ngoài rollback — tức là mất tiền user. {@code MANDATORY}
     * biến lỗi đó thành {@link org.springframework.transaction.IllegalTransactionStateException}
     * ngay tại chỗ, lúc chạy test.</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public com.catcheck.credit.api.CreditConsumption.CreditCharge consume(
            UUID userId, com.catcheck.credit.api.CreditConsumption.CreditConsumeCommand command) {

        Optional<Replay> replay = findReplay(userId, command.idempotencyKey());
        if (replay.isPresent()) {
            LedgerEntry keyRow = replay.get().keyRow();
            // Dựng lại TOÀN BỘ lần trừ, không chỉ dòng mang khoá: khoá UNIQUE một cột nên chỉ dòng
            // đầu mang khoá, và trả `charged` bằng phần của lô đầu sẽ báo nhỏ hơn số thực đã trừ.
            return replay.get().toCharge(creditLedgerPort.findConsumeRowsByRef(
                    command.refType(), command.refId()));
        }

        Instant now = clock.instant();
        List<CreditBatchSnapshot> candidates = creditLedgerPort.lockLiveBatchesForFefo(userId, now);
        int available = candidates.stream().mapToInt(CreditBatchSnapshot::remainingAmount).sum();

        if (available < command.credits()) {
            int trialRemaining = trialScansRemaining(userId);
            throw new BusinessRuleException(
                    com.catcheck.credit.api.CreditErrorCode.CREDIT_INSUFFICIENT, available, trialRemaining);
        }

        requireWriteAccess(userId, now);

        List<Deduction> deductions = deductFefo(candidates, command.credits());
        int balanceAfter = creditLedgerPort.availableBalance(userId, now);

        List<com.catcheck.credit.api.CreditConsumption.ChargedBatch> charged = new ArrayList<>(deductions.size());
        boolean firstRow = true;
        for (Deduction deduction : deductions) {
            LedgerEntry entry = new LedgerEntry(
                    uuidV7.generate(),
                    userId,
                    deduction.batchId(),
                    com.catcheck.credit.domain.CreditLedgerType.CONSUME,
                    -deduction.amount(),
                    balanceAfter,
                    command.refType(),
                    command.refId(),
                    // Dòng ĐẦU mang khoá của bên gọi. Với lần trừ vắt qua nhiều lô thì các
                    // dòng sau để null: một lần trừ là MỘT transaction, nên chạm UNIQUE ở dòng
                    // đầu đã đủ để chặn replay toàn bộ (rollback trọn vẹn).
                    firstRow ? command.idempotencyKey() : null,
                    command.note(),
                    now);
            firstRow = false;
            creditLedgerPort.append(entry);
            charged.add(new com.catcheck.credit.api.CreditConsumption.ChargedBatch(
                    deduction.batchId(), entry.id(), deduction.amount()));
        }

        return new com.catcheck.credit.api.CreditConsumption.CreditCharge(
                command.credits(), balanceAfter, charged);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Hoàn về <b>đúng lô đã trừ</b> (p5 R7) bằng cách đọc {@code batch_id} của dòng ledger
     * gốc — không tự chọn lại theo FEFO, vì số đã trừ phải về đúng chỗ đã lấy ra.</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public com.catcheck.credit.api.CreditConsumption.CreditRefund refund(
            UUID userId, com.catcheck.credit.api.CreditConsumption.CreditRefundCommand command) {

        Optional<Replay> replay = findReplay(userId, command.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get().toRefund();
        }

        CreditLedgerPort.LedgerEntryRef original =
                creditLedgerPort.findById(command.consumedLedgerEntryId())
                        .orElseThrow(() -> refundRejected());

        // Ba trường hợp này cùng trả một lỗi: không có dòng, không phải của user này, và là
        // GRANT/REFUND (không phải dòng đã lấy credit ra). Trả chung để không dò được trạng thái
        // của dòng ledger người khác.
        if (!original.userId().equals(userId) || !original.isRefundable()) {
            throw refundRejected();
        }
        // UNIQUE(idempotency_key) chỉ chặn hoàn TRÙNG KHOÁ, không chặn hoàn hai lần bằng hai
        // khoá khác nhau. p5 R7 chỉ có đúng một khoản hoàn cho mỗi lần trừ — hoàn hai lần là tiền
        // phát sinh từ hư không.
        if (creditLedgerPort.existsRefundReferencing(original.id())) {
            throw refundRejected();
        }

        Instant now = clock.instant();
        // Khoá đúng lô gốc, KHÔNG khoá danh sách FEFO: lý do phổ biến nhất để hoàn là lô đó đã
        // cạn hoặc đã quá hạn, mà lockLiveBatchesForFefo lọc ra cả hai (remaining > 0 và
        // expires_at > now). Dùng hàm đó thì hoàn thất bại đúng lúc user cần nhất.
        CreditBatchSnapshot target = creditLedgerPort.lockBatchForRefund(original.batchId())
                .orElseThrow(() -> refundRejected());

        int refunded = -original.amount();
        if (!target.canRefund(refunded)) {
            // Lô đã được hoàn tới mức tối đa (ví dụ chính bộ phân tích đã sửa và chạy lại nhiều
            // lần trên cùng một dòng gốc). Bỏ qua lỗi CHECK ở tầng DB, ném lỗi nghiệp vụ ở đây.
            throw refundRejected();
        }

        creditLedgerPort.updateRemaining(target.id(), target.remainingAmount() + refunded);
        // Lô vừa được cộng trở lại có thể đã hết số (nếu trước đó remaining = 0) — mở lại để FEFO
        // thấy nó, nếu không số vừa hoàn sẽ nằm im trong DB mà không bao giờ dùng được.
        creditLedgerPort.markActive(target.id());
        int balanceAfter = creditLedgerPort.availableBalance(userId, now);

        LedgerEntry refundEntry = new LedgerEntry(
                uuidV7.generate(),
                userId,
                target.id(),
                com.catcheck.credit.domain.CreditLedgerType.REFUND,
                refunded,
                balanceAfter,
                null,
                command.consumedLedgerEntryId(),
                command.idempotencyKey(),
                command.note(),
                now);
        creditLedgerPort.append(refundEntry);

        return new com.catcheck.credit.api.CreditConsumption.CreditRefund(
                target.id(), refundEntry.id(), refunded, balanceAfter);
    }

    /**
     * Lỗi chung cho mọi nguyên nhân hoàn bị từ chối. Dùng {@code CREDIT_INSUFFICIENT} vì đây là mã
     * duy nhất trong {@link com.catcheck.credit.api.CreditErrorCode} nói về số dư credit, và thông
     * điệp của nó ("không đủ credit") đúng nghĩa: không có credit nào để trả về chỗ này.
     * p8 §8.2.4 không có mã riêng cho "dòng hoàn không hợp lệ" — xem handover A4.
     */
    private BusinessRuleException refundRejected() {
        return new BusinessRuleException(
                com.catcheck.credit.api.CreditErrorCode.CREDIT_INSUFFICIENT, 0, 0);
    }

    @Override
    public boolean hasAvailableCredit(UUID userId) {
        return creditLedgerPort.availableBalance(userId, clock.instant()) > 0;
    }

    @Override
    public boolean hasTrialScanRemaining(UUID userId) {
        return trialScansRemaining(userId) > 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Dùng {@code UPDATE ... SET trial_scans_used = trial_scans_used + 1} có điều kiện, không
     * đọc-rồi-ghi trong Java — nếu không thì hai lượt trial gửi song song sẽ thành ba
     * (bất biến: số lượt trial phải là số nguyên, không bao giờ vượt hạn mức).</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public boolean consumeTrialScan(UUID userId) {
        Instant now = clock.instant();
        userEntitlementPort.ensureRow(userId, now);
        return userEntitlementPort.incrementTrialScansUsed(userId, TRIAL_SCAN_LIMIT, now);
    }

    private int trialScansRemaining(UUID userId) {
        return Math.max(0, TRIAL_SCAN_LIMIT - userEntitlementPort.findOrDefault(userId, clock.instant()).trialScansUsed());
    }

    /**
     * Quyền tạo MỚI hết hạn cùng credit (p5 R5). Chỉ kiểm tra khi user thực sự có credit lô đang
     * hiệu lực: nếu có lô thì {@code write_access_until} theo bất biến I28 phải bằng
     * {@code max(expires_at)} nên hợp lệ là luôn đủ — nên khi phần này bắt thì nghĩa là
     * {@code write_access_until} đang lệch so với {@code credit_batch}, và
     * {@code InvariantAuditJob} (p4 §4.5.2, kiểm I28) sẽ báo.
     */
    private void requireWriteAccess(UUID userId, Instant now) {
        if (!userEntitlementPort.findOrDefault(userId, now).hasWriteAccessAt(now)) {
            throw new PermissionDeniedException(
                    com.catcheck.credit.api.CreditErrorCode.WRITE_ACCESS_EXPIRED);
        }
    }

    /**
     * Vắt tiền qua các lô theo thứ tự {@code expires_at} tăng dần. Danh sách đã được sắp và
     * khoá sẵn bởi {@link CreditLedgerPort#lockLiveBatchesForFefo}.
     */
    private List<Deduction> deductFefo(List<CreditBatchSnapshot> candidates, int requested) {
        List<Deduction> deductions = new ArrayList<>();
        int needed = requested;
        for (CreditBatchSnapshot batch : candidates) {
            if (needed == 0) {
                break;
            }
            int take = Math.min(needed, batch.remainingAmount());
            if (take <= 0) {
                continue;
            }
            int newRemaining = batch.remainingAmount() - take;
            creditLedgerPort.updateRemaining(batch.id(), newRemaining);
            if (newRemaining == 0) {
                creditLedgerPort.markExhausted(batch.id());
            }
            deductions.add(new Deduction(batch.id(), take));
            needed -= take;
        }
        if (needed > 0) {
            // Không xảy ra nếu available >= requested, nhưng nếu có thì đó là lỗi logic —
            // ném để transaction rollback chứ không ghi ledger nửa vời.
            log.error("FEFO thiếu {} credit dù đã khoá đủ lô; rollback để giữ bất biến I1", needed);
            throw new BusinessRuleException(
                    com.catcheck.credit.api.CreditErrorCode.CREDIT_INSUFFICIENT, requested - needed, 0);
        }
        return deductions;
    }

    /**
     * Tìm lần ghi trước theo khoá idempotency.
     *
     * <p>Chặn ở tầng ứng dụng trước khi ghi, thay vì để {@code UNIQUE} nổ lên thành 500 (p5 R8).</p>
     *
     * <p>Khoá thuộc về user trong khoá. Nếu khoá đã bị user KHÁC dùng thì đây là va chạm, không
     * phải retry — im lặng bỏ qua sẽ khiến lệnh chạm UNIQUE ở tầng DB. Module credit KHÔNG khai
     * báo mã lỗi riêng cho việc này: {@code IDEMPOTENCY_KEY_CONFLICT} (409) đã có sẵn trong
     * p8 §8.2.4 nhưng thuộc danh mục chung, và lớp {@code idempotency_record} của W3 phải trả nó
     * TRƯỚC khi request tới credit (khoá được giới hạn theo {@code (userId, method, path, key)}
     * nên người dùng chỉ có thể đưa ra khoá của chính mình). Đến được đây nghĩa là lớp bảo vệ
     * thứ nhất bị bỏ qua — một vi phạm bất biến, không phải lỗi nghiệp vụ, nên ném 500 kèm log
     * để báo động thay vì âm thầm. Xem {@code docs/handovers/A4.md}.</p>
     */
    private Optional<Replay> findReplay(UUID userId, String idempotencyKey) {
        Optional<LedgerEntry> existing = creditLedgerPort.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent() && !existing.get().userId().equals(userId)) {
            log.error("Khoá idempotency đã thuộc user khác — lớp idempotency_record (p8 §8.1.8) "
                            + "bị bỏ qua. entryId={} entryUserId={} refType={}",
                    existing.get().id(), existing.get().userId(), existing.get().refType());
            throw new IllegalStateException("idempotency-key thuộc về user khác: " + idempotencyKey);
        }
        return existing.map(Replay::new);
    }

    /** Một khoản đã trừ từ một lô. */
    private record Deduction(UUID batchId, int amount) {
    }

    /**
     * Dòng ledger đã tồn tại với cùng {@code idempotency_key} ⇒ client retry, trả lại kết quả
     * cũ thay vì trừ lần hai (p5 R8, bất biến I4, p8 §8.1.8).
     */
    private record Replay(LedgerEntry keyRow) {

        com.catcheck.credit.api.CreditConsumption.CreditCharge toCharge(
                List<LedgerEntry> chargeRows) {
            int charged = chargeRows.stream().mapToInt(LedgerEntry::amount).sum();
            return new com.catcheck.credit.api.CreditConsumption.CreditCharge(
                    -charged,
                    keyRow.balanceAfter(),
                    chargeRows.stream()
                            .map(row -> new com.catcheck.credit.api.CreditConsumption.ChargedBatch(
                                    row.batchId(), row.id(), -row.amount()))
                            .toList());
        }

        com.catcheck.credit.api.CreditConsumption.CreditRefund toRefund() {
            return new com.catcheck.credit.api.CreditConsumption.CreditRefund(
                    keyRow.batchId(), keyRow.id(), keyRow.amount(), keyRow.balanceAfter());
        }
    }
}
