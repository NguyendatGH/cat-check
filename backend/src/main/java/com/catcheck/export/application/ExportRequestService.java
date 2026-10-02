package com.catcheck.export.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.export.api.ExportErrorCode;
import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.port.ExportJobRepository;
import com.catcheck.export.domain.port.SubjectSnapshotPort;
import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code POST /api/v1/exports} (p8 §8.4.10 J1) — tạo {@code export_job} rồi xử lý NỀN
 * (p13 §13.6.1: sinh PDF tốn CPU nên KHÔNG đồng bộ trong request).
 */
@Service
public class ExportRequestService {

    private static final Set<String> VALID_SECTIONS = Set.of("TREND", "SCAN_LOG", "NOTES", "PROFILE");
    private static final long MAX_RANGE_DAYS = 366;

    private final ExportJobRepository jobRepository;
    private final SubjectSnapshotPort subjectSnapshotPort;
    private final ScanHistoryQuery scanHistoryQuery;
    private final DocumentCodeGenerator documentCodeGenerator;
    private final ExportGenerationService generationService;
    private final ThreadPoolTaskExecutor exportExecutor;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ExportRequestService(
            ExportJobRepository jobRepository,
            SubjectSnapshotPort subjectSnapshotPort,
            ScanHistoryQuery scanHistoryQuery,
            DocumentCodeGenerator documentCodeGenerator,
            ExportGenerationService generationService,
            ThreadPoolTaskExecutor exportExecutor,
            UuidV7 uuidV7,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.subjectSnapshotPort = subjectSnapshotPort;
        this.scanHistoryQuery = scanHistoryQuery;
        this.documentCodeGenerator = documentCodeGenerator;
        this.generationService = generationService;
        this.exportExecutor = exportExecutor;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public ExportJob request(ExportRequestCommand cmd) {
        SubjectSnapshotPort.CatProfile cat = subjectSnapshotPort.findCatProfile(cmd.catId())
                .filter(c -> c.ownerId().equals(cmd.userId()))
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));

        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        var range = resolveRange(cmd, today);

        jobRepository.findActiveByUser(cmd.userId())
                .ifPresent(existing -> {
                    throw new ConflictException(ExportErrorCode.EXPORT_JOB_IN_PROGRESS);
                });

        Instant fromInstant = range.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = range.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<ScanHistoryQuery.ExportScanRow> scans = scanHistoryQuery.scansForExport(cat.catId(), fromInstant, toInstant);
        if (scans.isEmpty()) {
            throw new BusinessRuleException(ExportErrorCode.EXPORT_NO_DATA);
        }

        List<String> sections = cmd.sections() == null || cmd.sections().isEmpty()
                ? List.of("TREND", "SCAN_LOG", "NOTES", "PROFILE")
                : cmd.sections().stream().filter(VALID_SECTIONS::contains).toList();

        String documentCode = documentCodeGenerator.generate();
        Instant now = clock.instant();
        ExportJob job = new ExportJob(uuidV7.generate(), cmd.userId(), cat.catId(), documentCode,
                range.from(), range.to(), cmd.rangePreset(), sections,
                cmd.locale() == null ? "vi" : cmd.locale(),
                cmd.timezone() == null ? "Asia/Ho_Chi_Minh" : cmd.timezone(), now);
        try {
            job = jobRepository.save(job);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Lưới an toàn cuối: UNIQUE(user_id) WHERE status IN ('QUEUED','RUNNING') ở DB (p4 G1)
            // bắt race hiếm giữa hai request đồng thời mà kiểm tra findActiveByUser() ở trên bỏ lỡ.
            throw new ConflictException(ExportErrorCode.EXPORT_JOB_IN_PROGRESS);
        }

        UUID jobId = job.getId();
        exportExecutor.execute(() -> generationService.generate(jobId));

        return job;
    }

    private record Range(LocalDate from, LocalDate to) {
    }

    private Range resolveRange(ExportRequestCommand cmd, LocalDate today) {
        LocalDate to = cmd.rangeTo() != null ? cmd.rangeTo() : today;
        LocalDate from;
        if (cmd.rangePreset() != null) {
            from = switch (cmd.rangePreset()) {
                case "7D" -> to.minusDays(6);
                case "30D" -> to.minusDays(29);
                case "90D" -> to.minusDays(89);
                case "CUSTOM" -> cmd.rangeFrom();
                default -> throw new BusinessRuleException(ExportErrorCode.EXPORT_RANGE_INVALID);
            };
        } else {
            from = cmd.rangeFrom() != null ? cmd.rangeFrom() : to.minusDays(29);
        }
        if (from == null || to.isBefore(from) || to.isAfter(today)
                || java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessRuleException(ExportErrorCode.EXPORT_RANGE_INVALID);
        }
        return new Range(from, to);
    }
}
