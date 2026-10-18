package com.catcheck.export.application;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.port.ExportJobRepository;
import com.catcheck.export.domain.port.PdfRenderer;
import com.catcheck.export.domain.port.ReportStorage;
import com.catcheck.export.domain.port.SubjectSnapshotPort;
import com.catcheck.insight.api.HealthFlagExportQuery;
import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.shared.i18n.MessageResolver;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ExportGenerationService.class);

    private final ExportJobRepository jobRepository;
    private final SubjectSnapshotPort subjectSnapshotPort;
    private final ScanHistoryQuery scanHistoryQuery;
    private final HealthFlagExportQuery healthFlagExportQuery;
    private final PdfRenderer pdfRenderer;
    private final ReportStorage reportStorage;
    private final MessageResolver messages;
    private final Clock clock;

    public ExportGenerationService(
            ExportJobRepository jobRepository,
            SubjectSnapshotPort subjectSnapshotPort,
            ScanHistoryQuery scanHistoryQuery,
            HealthFlagExportQuery healthFlagExportQuery,
            PdfRenderer pdfRenderer,
            ReportStorage reportStorage,
            MessageResolver messages,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.subjectSnapshotPort = subjectSnapshotPort;
        this.scanHistoryQuery = scanHistoryQuery;
        this.healthFlagExportQuery = healthFlagExportQuery;
        this.pdfRenderer = pdfRenderer;
        this.reportStorage = reportStorage;
        this.messages = messages;
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

        Locale locale = Locale.forLanguageTag(job.getLocale());
        try {
            ExportRenderData data = gatherData(job, locale);
            String html = new PdfDocumentBuilder(messages, locale).build(data);
            PdfRenderer.Result rendered = pdfRenderer.render(html);

            ReportStorage.Stored stored = reportStorage.put(job.getDocumentCode(), rendered.bytes());

            job.markReady(stored.provider(), stored.fileRef(), rendered.bytes().length,
                    rendered.pageCount(), data.scans().size(), data.chartVersionSnapshot(), clock.instant());
            jobRepository.save(job);
        } catch (Exception ex) {
            log.error("export_job {} that bai: {}", jobId, ex.getMessage());
            job.markFailed(messages.resolve("export.pdf.failure.generic", locale), clock.instant());
            jobRepository.save(job);
        }
    }

    public void markRejected(UUID jobId, Instant now) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(messages.resolve("export.pdf.failure.busy", Locale.forLanguageTag(job.getLocale())), now);
            jobRepository.save(job);
        });
    }

    private ExportRenderData gatherData(ExportJob job, Locale locale) {
        ZoneId zone = ZoneId.of(job.getTimezone());
        DateTimeFormatter dateTimeLabel = DateTimeFormatter.ofPattern(
                messages.resolve("export.pdf.date_pattern", locale) + " HH:mm", locale);
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
                LocalDateTime.ofInstant(s.capturedAt(), zone).format(dateTimeLabel),
                s.phValue(),
                symbolFor(s.classification()),
                labelFor(s.classification(), locale),
                confidenceLabel(s.confidence(), locale),
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
                sexLabel(cat.sex(), locale),
                ageLabel(cat, locale),
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

    private String labelFor(String classification, Locale locale) {
        return messages.resolve(switch (classification) {
            case "IN_RANGE" -> "export.pdf.classification.in_range";
            case "SLIGHTLY_HIGH" -> "export.pdf.classification.slightly_high";
            case "SLIGHTLY_LOW" -> "export.pdf.classification.slightly_low";
            case "HIGH" -> "export.pdf.classification.high";
            case "LOW" -> "export.pdf.classification.low";
            default -> "export.pdf.classification.unknown";
        }, locale);
    }

    private String confidenceLabel(BigDecimal confidence, Locale locale) {
        if (confidence == null) {
            return "—";
        }
        double v = confidence.doubleValue();
        if (v >= 0.75) {
            return messages.resolve("export.pdf.confidence.high", locale);
        }
        return messages.resolve(v >= 0.40 ? "export.pdf.confidence.medium" : "export.pdf.confidence.low", locale);
    }

    private String sexLabel(String sex, Locale locale) {
        if (sex == null) {
            return null;
        }
        return messages.resolve(switch (sex) {
            case "MALE" -> "export.pdf.sex.male";
            case "FEMALE" -> "export.pdf.sex.female";
            default -> "export.pdf.sex.unknown";
        }, locale);
    }

    private String ageLabel(SubjectSnapshotPort.CatProfile cat, Locale locale) {
        if (cat.birthDate() != null) {
            Period period = Period.between(cat.birthDate(), clock.instant().atZone(ZoneOffset.UTC).toLocalDate());
            return messages.resolve("export.pdf.age.years_months",
                    new Object[] {period.getYears(), period.getMonths()}, locale);
        }
        if (cat.approxAgeMonths() != null) {
            return messages.resolve("export.pdf.age.approx",
                    new Object[] {cat.approxAgeMonths() / 12, cat.approxAgeMonths() % 12}, locale);
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
