package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản trị mã kích hoạt — L19, L20, L21, L22, L23, L24 (p8 §8.4.12 mục (b)).
 *
 * <p>Việc sinh mã thật vẫn nằm ở {@link ActivationCodeIssuanceService} (nơi duy nhất mã thô tồn
 * tại); lớp này thêm phần <b>quản trị</b>: trần số lượng theo hợp đồng API, định danh lô, một
 * lần tải CSV, void, và {@code audit_log} cho mọi hành động (p14 §14.3.2 mục 4 — ba action
 * {@code ACTIVATION_CODE_BATCH_CREATE}, {@code ACTIVATION_CODE_CSV_DOWNLOAD},
 * {@code ACTIVATION_CODE_VOID}).</p>
 *
 * <p><b>Định danh lô = {@code production_batch}.</b> p8 L21/L22/L24 nói tới một {@code batchId},
 * nhưng p4 §4.9.2 không có bảng {@code activation_batch} và CLAUDE.md cấm thêm migration ngoài
 * danh mục. {@code production_batch} đã là thứ p5 §5.5 dùng để nối lô cát với {@code color_chart},
 * nên nó làm định danh lô mà không cần bịa khoá mới — với một điều kiện: nó phải duy nhất, nếu
 * không "cả lô" là một tập mã không xác định. Vì vậy {@link #issueBatch} từ chối
 * {@code 409 ACTIVATION_BATCH_EXISTS} khi lô đã có mã. Xem handoff H15.98.</p>
 */
@Service
public class ActivationCodeAdminService {

    private static final Logger log = LoggerFactory.getLogger(ActivationCodeAdminService.class);

    /** Dòng tiêu đề CSV. Không có cột nào ngoài bốn cột này — xem {@link #toCsv}. */
    private static final String CSV_HEADER = "code,package_code,production_batch,valid_until";

    private final ActivationCodePort codePort;
    private final ActivationCodeIssuanceService issuanceService;
    private final ActivationCsvVault csvVault;
    private final AuditLogService auditLogService;

    public ActivationCodeAdminService(ActivationCodePort codePort,
                                      ActivationCodeIssuanceService issuanceService,
                                      ActivationCsvVault csvVault,
                                      AuditLogService auditLogService) {
        this.codePort = codePort;
        this.issuanceService = issuanceService;
        this.csvVault = csvVault;
        this.auditLogService = auditLogService;
    }

    /* ------------------------------------------------------------------ L19 */

    @Transactional(readOnly = true)
    public Page<ActivationCode> search(ActivationCodeFilter filter, int page, int size) {
        return new Page<>(
                codePort.search(filter, page * size, size),
                codePort.count(filter));
    }

    /* ------------------------------------------------------------------ L20 */

    /**
     * Sinh một lô mã.
     *
     * <p>Thứ tự kiểm là có chủ ý: trần số lượng trước (từ chối rẻ nhất), rồi trùng lô, rồi mới
     * tới {@link ActivationCodeIssuanceService#issue} — nơi kiểm gói tồn tại và thực sự ghi.</p>
     *
     * @return lô vừa tạo; CSV mã thô nằm trong {@link ActivationCsvVault} chờ đúng một lần đọc
     */
    @Transactional
    public ActivationBatchSummary issueBatch(String packageCode, int quantity, String productionBatch,
                                             int validForDays, AdminActionContext context) {
        if (quantity < 1 || quantity > ActivationCodeIssuanceService.MAX_CODES_PER_BATCH) {
            throw new BusinessRuleException(CreditErrorCode.ACTIVATION_BATCH_TOO_LARGE,
                    ActivationCodeIssuanceService.MAX_CODES_PER_BATCH);
        }
        String batchId = requireBatchId(productionBatch);
        if (codePort.findBatch(batchId).isPresent()) {
            throw new ConflictException(CreditErrorCode.ACTIVATION_BATCH_EXISTS, batchId);
        }

        ActivationCodeIssuanceService.IssuedBatch issued =
                issuanceService.issue(packageCode, quantity, batchId, validForDays);

        // CSV vào bộ nhớ, KHÔNG vào DB và KHÔNG vào log (p5 §5.9).
        csvVault.store(batchId, toCsv(issued));

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.SUBSCRIPTION, null)
                .action("ACTIVATION_CODE_BATCH_CREATE")
                .outcome(AuditOutcome.SUCCESS)
                .meta("packageCode", issued.packageCode())
                .meta("productionBatch", batchId)
                .meta("quantity", quantity)
                .meta("validUntil", String.valueOf(issued.validUntil()))
                .meta("reason", context.reason())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        // Log chỉ có số lượng + mã gói + lô. Method này KHÔNG đọc field nào tên khớp regex PII
        // của R16, và biến `issued` giữ mã thô thì không bao giờ đi vào logger.
        log.info("Admin sinh lô mã: packageCode={} productionBatch={} quantity={}",
                issued.packageCode(), batchId, quantity);

        return new ActivationBatchSummary(batchId, issued.packageCode(), quantity, quantity, 0, 0,
                issued.issuedAt(), issued.validUntil());
    }

    /* ------------------------------------------------------------------ L21 */

    @Transactional(readOnly = true)
    public Page<ActivationBatchSummary> listBatches(int page, int size) {
        return new Page<>(codePort.listBatches(page * size, size), codePort.countBatches());
    }

    /** CSV của lô này còn tải được không — cột {@code csvAvailable}. */
    public boolean csvAvailable(String batchId) {
        return csvVault.isAvailable(batchId);
    }

    /* ------------------------------------------------------------------ L22 */

    /**
     * Tải CSV mã thô — <b>một lần duy nhất</b>.
     *
     * @throws NotFoundException     {@code 404} lô không tồn tại
     * @throws BusinessRuleException {@code 410 ACTIVATION_CSV_ALREADY_DOWNLOADED} nếu đã tải
     *                               (hoặc mã thô không còn trong bộ nhớ)
     */
    @Transactional
    public String downloadCsv(String batchId, AdminActionContext context) {
        ActivationBatchSummary batch = requireBatch(batchId);
        String csv = csvVault.drain(batchId)
                .orElseThrow(() -> new BusinessRuleException(
                        CreditErrorCode.ACTIVATION_CSV_ALREADY_DOWNLOADED));

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.SUBSCRIPTION, null)
                .action("ACTIVATION_CODE_CSV_DOWNLOAD")
                .outcome(AuditOutcome.SUCCESS)
                .meta("productionBatch", batchId)
                .meta("packageCode", batch.packageCode())
                .meta("codeCount", batch.totalCodes())
                .meta("reason", context.reason())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
        return csv;
    }

    /* ------------------------------------------------------------------ L23 */

    /**
     * Vô hiệu hoá một mã.
     *
     * <p>Mã {@code REDEEMED} không void được (p14 §14.3.2 mục 4: đã tạo {@code credit_batch},
     * muốn thu hồi phải qua nghiệp vụ điều chỉnh credit) ⇒ {@code 409
     * ACTIVATION_CODE_ALREADY_USED}. Mã đã {@code VOID} thì trả về như không có gì xảy ra —
     * void là thao tác idempotent, bấm hai lần không phải lỗi.</p>
     */
    @Transactional
    public ActivationCode voidCode(UUID codeId, AdminActionContext context) {
        ActivationCode code = codePort.findById(codeId)
                .orElseThrow(() -> new NotFoundException(CreditErrorCode.ACTIVATION_CODE_INVALID));
        if (code.status() == ActivationCodeStatus.REDEEMED) {
            throw new ConflictException(CreditErrorCode.ACTIVATION_CODE_ALREADY_USED,
                    String.valueOf(code.redeemedAt()), false);
        }
        boolean changed = code.status() == ActivationCodeStatus.ISSUED && codePort.markVoid(codeId);

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.SUBSCRIPTION, null)
                .action("ACTIVATION_CODE_VOID")
                .outcome(AuditOutcome.SUCCESS)
                .meta("codeId", codeId.toString())
                .meta("packageCode", code.packageCode())
                .meta("productionBatch", code.productionBatch())
                .meta("changed", changed)
                .meta("reason", context.reason())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return codePort.findById(codeId).orElse(code);
    }

    /* ------------------------------------------------------------------ L24 */

    /**
     * Vô hiệu hoá cả lô (in hỏng, thu hồi lô sản xuất).
     *
     * @return số mã thực sự chuyển sang {@code VOID} — mã đã {@code REDEEMED} không bị đụng tới
     */
    @Transactional
    public int voidBatch(String batchId, AdminActionContext context) {
        ActivationBatchSummary batch = requireBatch(batchId);
        int voided = codePort.markBatchVoid(batchId);

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.SUBSCRIPTION, null)
                .action("ACTIVATION_CODE_VOID")
                .outcome(AuditOutcome.SUCCESS)
                .meta("productionBatch", batchId)
                .meta("packageCode", batch.packageCode())
                .meta("voidedCodes", voided)
                .meta("redeemedCodesUntouched", batch.redeemedCodes())
                .meta("reason", context.reason())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        log.info("Admin vô hiệu hoá lô {}: {} mã chuyển VOID", batchId, voided);
        return voided;
    }

    @Transactional(readOnly = true)
    public Optional<ActivationBatchSummary> findBatch(String batchId) {
        return codePort.findBatch(batchId);
    }

    private ActivationBatchSummary requireBatch(String batchId) {
        return codePort.findBatch(requireBatchId(batchId))
                .orElseThrow(() -> new NotFoundException(CreditErrorCode.ACTIVATION_BATCH_NOT_FOUND));
    }

    private static String requireBatchId(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId (production_batch) phải có giá trị");
        }
        return batchId.strip();
    }

    /**
     * CSV cho khâu in bao bì. RFC 4180: bọc ngoặc kép khi giá trị có dấu phẩy/ngoặc/xuống dòng.
     *
     * <p>Mã kích hoạt theo {@code ActivationCodeFormat} chỉ gồm {@code A-Z0-9-} nên không bao
     * giờ cần bọc, nhưng {@code production_batch} là chuỗi admin tự nhập — không escape thì một
     * dấu phẩy trong tên lô làm lệch cả cột của 50 000 dòng.</p>
     */
    private static String toCsv(ActivationCodeIssuanceService.IssuedBatch issued) {
        StringBuilder csv = new StringBuilder(issued.rawCodes().size() * 32);
        csv.append(CSV_HEADER).append('\n');
        String packageCode = escape(issued.packageCode());
        String productionBatch = escape(issued.productionBatch());
        String validUntil = issued.validUntil() == null ? "" : issued.validUntil().toString();
        for (String raw : issued.rawCodes()) {
            csv.append(raw).append(',')
                    .append(packageCode).append(',')
                    .append(productionBatch).append(',')
                    .append(validUntil).append('\n');
        }
        return csv.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * Một trang kết quả admin: dòng của trang + tổng số dòng.
     *
     * <p>Không trả về {@code Page} của Spring Data: R7 chỉ cho {@code ..infrastructure.persistence..}
     * phụ thuộc {@code org.springframework.data..}.</p>
     */
    public record Page<T>(List<T> items, long totalElements) {

        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
