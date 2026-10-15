package com.catcheck.export.application;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.port.ExportJobRepository;
import com.catcheck.export.domain.port.PdfRenderer;
import com.catcheck.export.domain.port.ReportStorage;
import com.catcheck.export.domain.port.SubjectSnapshotPort;
import com.catcheck.insight.api.HealthFlagExportQuery;
import com.catcheck.scan.api.ScanHistoryQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ExportGenerationService.class);
    private static final DateTimeFormatter DATETIME_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExportJobRepository jobRepository;
    private final SubjectSnapshotPort subjectSnapshotPort;
    private final ScanHistoryQuery scanHistoryQuery;
    private final HealthFlagExportQuery healthFlagExportQuery;
    private final PdfRenderer pdfRenderer;
    private final ReportStorage reportStorage;
    private final Clock clock;

    public ExportGenerationService(
            ExportJobRepository jobRepository,
            SubjectSnapshotPort subjectSnapshotPort,
            ScanHistoryQuery scanHistoryQuery,
            HealthFlagExportQuery healthFlagExportQuery,
            PdfRenderer pdfRenderer,
            ReportStorage reportStorage,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.subjectSnapshotPort = subjectSnapshotPort;
        this.scanHistoryQuery = scanHistoryQuery;
        this.healthFlagExportQuery = healthFlagExportQuery;
        this.pdfRenderer = pdfRenderer;
        this.reportStorage = reportStorage;
        this.clock = clock;
    }

    public void generate(UUID jobId) {
        Optional<ExportJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.warn("export_job {} bien mat truoc khi xu ly", jobId);
            return;
        }
        ExportJob job = jobOpt.get();
        job.markRunning();
        jobRepository.save(job);

        try {
            ExportRenderData data = gatherData(job);
            String html = PdfDocumentBuilder.build(data);
            PdfRenderer.Result rendered = pdfRenderer.render(html);

            ReportStorage.Stored stored = reportStorage.put(job.getDocumentCode(), rendered.bytes());

            job.markReady(stored.provider(), stored.fileRef(), rendered.bytes().length,
                    rendered.pageCount(), data.scans().size(), data.chartVersionSnapshot(), clock.instant());
            jobRepository.save(job);
        } catch (Exception ex) {
            log.error("export_job {} that bai: {}", jobId, ex.getMessage());
            job.markFailed(friendlyFailureReason(ex), clock.instant());
            jobRepository.save(job);
        }
    }

    public void markRejected(UUID jobId, Instant now) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed("He thong dang ban. Ban thu tao lai sau it phut nhe.", now);
            jobRepository.save(job);
        });
    }

    private String friendlyFailureReason(Exception ex) {
        return "Co loi khi sinh bao cao. Ban thu tao lai nhe.";
    }

    private ExportRenderData gatherData(ExportJob job) {
        ZoneId zone = ZoneId.of(job.getTimezone());
        // M23: ranh giới ngày theo múi giờ của job (cùng múi giờ dùng cho nhãn), không phải UTC.
        Instant fromInstant = job.getRangeFrom().atStartOfDay(zone).toInstant();
        Instant toInstant = job.getRangeTo().plusDays(1).atStartOfDay(zone).toInstant();

        SubjectSnapshotPort.CatProfile cat = subjectSnapshotPort.findCatProfile(job.getCatId()).orElseThrow();
        Optional<SubjectSnapshotPort.OwnerProfile> owner = subjectSnapshotPort.findOwnerProfile(job.getUserId());

        List<ScanHistoryQuery.ExportScanRow> scans = scanHistoryQuery.scansForExport(job.getCatId(), fromInstant, toInstant);
        List<HealthFlagExportQuery.ExportFlagRow> flags = healthFlagExportQuery.flagsInRange(job.getCatId(), fromInstant, toInstant);

        List<ScanHistoryQuery.ExportScanRow> nonDisputed = scans.stream().filter(s -> !s.disputed()).toList();
        List<BigDecimal> phValues = nonDisputed.stream().map(ScanHistoryQuery.ExportScanRow::phValue)
                .filter(java.util.Objects::nonNull).toList();

        long outOfRange = nonDisputed.stream()
                .filter(s -> !"IN_RANGE".equals(s.classification()) && !s.nearBoundary()).count();
        long nearBoundary = nonDisputed.stream().filter(ScanHistoryQuery.ExportScanRow::nearBoundary).count();
        boolean anyNonCardCcm = scans.stream().anyMatch(s -> !"CARD_CCM".equals(s.calibrationMethod()));
        Integer chartVersionSnapshot = null;

        List<ExportRenderData.ScanRow> scanRows = scans.stream().map(s -> new ExportRenderData.ScanRow(
                LocalDateTime.ofInstant(s.capturedAt(), zone).format(DATETIME_LABEL),
                s.phValue(),
                symbolFor(s.classification()),
                labelFor(s.classification()),
                confidenceLabel(s.confidence()),
                s.qualityFlagCodes().isEmpty() ? null : String.join(", ", s.qualityFlagCodes()),
                s.disputed(),
                s.imageAvailable())).toList();

        List<SvgTrendChartBuilder.Point> points = nonDisputed.stream()
                .filter(s -> s.phValue() != null)
                .sorted(java.util.Comparator.comparing(ScanHistoryQuery.ExportScanRow::capturedAt))
                .map(s -> new SvgTrendChartBuilder.Point(
                        LocalDateTime.ofInstant(s.capturedAt(), zone).toLocalDate(),
                        s.phValue().doubleValue(),
                        !"IN_RANGE".equals(s.classification()) && !s.nearBoundary()))
                .toList();
        String svgChart = SvgTrendChartBuilder.build(points);

        List<ExportRenderData.FlagRow> flagRows = flags.stream()
                .map(f -> new ExportRenderData.FlagRow(f.ruleCode(), f.severity(), f.explanationVi(), f.triggeredAt()))
                .toList();

        return new ExportRenderData(
                job.getDocumentCode(),
                cat.name(),
                cat.breedLabel(),
                sexLabel(cat.sex()),
                ageLabel(cat),
                cat.weightKg() == null ? null : cat.weightKg() + " kg",
                owner.map(SubjectSnapshotPort.OwnerProfile::fullName).orElse(null),
                job.getRangeFrom(),
                job.getRangeTo(),
                LocalDateTime.ofInstant(clock.instant(), zone),
                scanRows,
                phValues.size(),
                phValues.stream().min(BigDecimal::compareTo).orElse(null),
                phValues.stream().max(BigDecimal::compareTo).orElse(null),
                median(phValues),
                outOfRange,
                nearBoundary,
                svgChart,
                anyNonCardCcm,
                flagRows,
                chartVersionSnapshot,
                null,
                job.getSections());
    }

    private String symbolFor(String classification) {
        return switch (classification) {
            case "IN_RANGE" -> "●";
            case "SLIGHTLY_HIGH" -> "▲";
            case "SLIGHTLY_LOW" -> "▼";
            case "HIGH" -> "▲▲";
            case "LOW" -> "▼▼";
            default -> "?";
        };
    }

    private String labelFor(String classification) {
        return switch (classification) {
            case "IN_RANGE" -> "Trong khoảng tham chiếu";
            case "SLIGHTLY_HIGH" -> "Hơi cao (thiên kiềm)";
            case "SLIGHTLY_LOW" -> "Hơi thấp (thiên axit)";
            case "HIGH" -> "Cao rõ rệt (thiên kiềm)";
            case "LOW" -> "Thấp rõ rệt (thiên axit)";
            default -> "Chưa đủ dữ liệu để kết luận";
        };
    }

    private String confidenceLabel(BigDecimal confidence) {
        if (confidence == null) {
            return "—";
        }
        double v = confidence.doubleValue();
        if (v >= 0.75) {
            return "Cao";
        }
        return v >= 0.40 ? "Trung bình" : "Thấp";
    }

    private String sexLabel(String sex) {
        if (sex == null) {
            return null;
        }
        return switch (sex) {
            case "MALE" -> "Mèo đực";
            case "FEMALE" -> "Mèo cái";
            default -> "Chưa rõ";
        };
    }

    private String ageLabel(SubjectSnapshotPort.CatProfile cat) {
        if (cat.birthDate() != null) {
            Period period = Period.between(cat.birthDate(), clock.instant().atZone(ZoneOffset.UTC).toLocalDate());
            return period.getYears() + " tuổi " + period.getMonths() + " tháng";
        }
        if (cat.approxAgeMonths() != null) {
            return (cat.approxAgeMonths() / 12) + " tuổi " + (cat.approxAgeMonths() % 12) + " tháng (ước lượng)";
        }
        return null;
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
