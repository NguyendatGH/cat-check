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

@Service
public class ActivationCodeAdminService {

    private static final Logger log = LoggerFactory.getLogger(ActivationCodeAdminService.class);

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


    @Transactional(readOnly = true)
    public Page<ActivationCode> search(ActivationCodeFilter filter, int page, int size) {
        return new Page<>(
                codePort.search(filter, page * size, size),
                codePort.count(filter));
    }


    @Transactional
    public ActivationBatchSummary issueBatch(String packageCode, int quantity, String productionBatch,
                                             int validForDays, AdminActionContext context) {
        if (quantity < 1 || quantity > ActivationCodeIssuanceService.MAX_CODES_PER_BATCH) {
            throw new BusinessRuleException(CreditErrorCode.ACTIVATION_BATCH_TOO_LARGE,
                    ActivationCodeIssuanceService.MAX_CODES_PER_BATCH);
        }
        String batchId = requireBatchId(productionBatch);
        codePort.lockProductionBatch(batchId);
        if (codePort.findBatch(batchId).isPresent()) {
            throw new ConflictException(CreditErrorCode.ACTIVATION_BATCH_EXISTS, batchId);
        }

        ActivationCodeIssuanceService.IssuedBatch issued =
                issuanceService.issue(packageCode, quantity, batchId, validForDays);

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

        log.info("Admin sinh lô mã: packageCode={} productionBatch={} quantity={}",
                issued.packageCode(), batchId, quantity);

        return new ActivationBatchSummary(batchId, issued.packageCode(), quantity, quantity, 0, 0,
                issued.issuedAt(), issued.validUntil());
    }


    @Transactional(readOnly = true)
    public Page<ActivationBatchSummary> listBatches(int page, int size) {
        return new Page<>(codePort.listBatches(page * size, size), codePort.countBatches());
    }

    public boolean csvAvailable(String batchId) {
        return csvVault.isAvailable(batchId);
    }


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

    public record Page<T>(List<T> items, long totalElements) {

        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
