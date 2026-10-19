package com.catcheck.export.application;

import com.catcheck.export.api.ExportErrorCode;
import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.ExportStatus;
import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M23 — cửa sổ dữ liệu của export tính theo ngày lịch ở múi giờ của job, không phải UTC.
 * M22 — {@code export_job.sections} đi tới tận HTML được render.
 */
class ExportTimezoneWindowTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    /** 20:00Z ngày 03/10 = 03:00 ngày 04/10 giờ Việt Nam. */
    private static final Instant NOW = Instant.parse("2026-10-03T20:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeExportJobRepository repository = new FakeExportJobRepository();
    private final RecordingScanQuery scanQuery = new RecordingScanQuery();
    private final List<String> renderedHtml = new ArrayList<>();

    private final ExportGenerationService generationService = new ExportGenerationService(
            repository,
            ExportTestDoubles.catOwnedBy(CAT_ID, USER_ID),
            scanQuery,
            ExportTestDoubles.noFlags(),
            xhtml -> {
                renderedHtml.add(xhtml);
                return new com.catcheck.export.domain.port.PdfRenderer.Result(new byte[16], 1);
            },
            new ExportTestDoubles.InMemoryReportStorage(),
            ExportTestDoubles.messages(),
            clock);

    private final ExportRequestService requestService = new ExportRequestService(
            repository,
            ExportTestDoubles.catOwnedBy(CAT_ID, USER_ID),
            scanQuery,
            new DocumentCodeGenerator(repository, clock),
            new ExportJobDispatcher(command -> { }, generationService, clock),
            userId -> true,
            new UuidV7(clock),
            clock);

    @Test
    @DisplayName("M23 — request: ranh giới ngày và 'hôm nay' tính theo Asia/Ho_Chi_Minh")
    void requestUsesTheJobZoneForBounds() {
        // 04/10 là "hôm nay" ở Việt Nam nhưng là "ngày mai" theo UTC — bản lỗi trả 400.
        ExportJob job = requestService.request(new ExportRequestCommand(USER_ID, CAT_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4), "CUSTOM", List.of("TREND"), "vi",
                "Asia/Ho_Chi_Minh"));

        assertThat(job.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(scanQuery.calls).containsExactly(new Instant[] {
                Instant.parse("2026-09-30T17:00:00Z"), Instant.parse("2026-10-04T17:00:00Z")});
    }

    @Test
    @DisplayName("M23 — múi giờ không hợp lệ bị từ chối 400 thay vì tạo job hỏng")
    void invalidZoneIsRejected() {
        assertThatThrownBy(() -> requestService.request(new ExportRequestCommand(USER_ID, CAT_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), "CUSTOM", null, "vi", "Mars/Base")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExportErrorCode.EXPORT_RANGE_INVALID);
    }

    @Test
    @DisplayName("M23/M22 — generate: bounds theo múi giờ job, chỉ render khối đã chọn")
    void generationUsesJobZoneAndSelectedSections() {
        ExportJob job = repository.save(new ExportJob(UUID.randomUUID(), USER_ID, CAT_ID, "CC-EXP-2026-000001",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D", List.of("TREND"), "vi",
                "Asia/Ho_Chi_Minh", NOW));

        generationService.generate(job.getId());

        assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(scanQuery.calls).containsExactly(new Instant[] {
                Instant.parse("2026-09-03T17:00:00Z"), Instant.parse("2026-10-03T17:00:00Z")});
        assertThat(renderedHtml).singleElement().satisfies(html -> assertThat(html)
                .contains("Biểu đồ pH theo thời gian")
                .doesNotContain("Bảng chi tiết từng lần đo")
                .contains("disclaimer-block"));
    }

    @Test
    @DisplayName("i18n — request: locale của command được lưu vào job")
    void requestStoresTheCommandLocale() {
        ExportJob job = requestService.request(new ExportRequestCommand(USER_ID, CAT_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), "CUSTOM", List.of("TREND"), "en",
                "Asia/Ho_Chi_Minh"));

        assertThat(job.getLocale()).isEqualTo("en");
    }

    @Test
    @DisplayName("i18n — request: không gửi locale thì dùng ngôn ngữ đã lưu của user")
    void requestFallsBackToTheProfileLocale() {
        ExportRequestService englishProfile = new ExportRequestService(repository,
                ExportTestDoubles.catOwnedBy(CAT_ID, USER_ID, "en"), scanQuery,
                new DocumentCodeGenerator(repository, clock),
                new ExportJobDispatcher(command -> { }, generationService, clock),
                userId -> true, new UuidV7(clock), clock);

        ExportJob job = englishProfile.request(new ExportRequestCommand(USER_ID, CAT_ID,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), "CUSTOM", List.of("TREND"), null,
                "Asia/Ho_Chi_Minh"));

        assertThat(job.getLocale()).isEqualTo("en");
    }

    @Test
    @DisplayName("i18n — generate: job en render PDF tiếng Anh theo locale của job")
    void englishJobRendersAnEnglishPdf() {
        ExportJob job = repository.save(new ExportJob(UUID.randomUUID(), USER_ID, CAT_ID, "CC-EXP-2026-000002",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D", List.of("SCAN_LOG", "PROFILE"), "en",
                "Asia/Ho_Chi_Minh", NOW));

        generationService.generate(job.getId());

        assertThat(renderedHtml).singleElement().satisfies(html -> assertThat(html)
                .contains("Detailed table of each measurement")
                .contains("<td>Female cat</td>")
                .contains("<td>3 years 0 months (estimated)</td>")
                .contains("<td>04 Oct 2026 02:00</td>")
                .contains("● Within reference range")
                .contains("<td>High</td>")
                .doesNotContain("Bảng chi tiết từng lần đo")
                .doesNotContain("Mèo cái")
                .doesNotContain("Trong khoảng tham chiếu"));
    }

    @Test
    @DisplayName("i18n — generate: job vi giữ nguyên nhãn tiếng Việt")
    void vietnameseJobKeepsTheVietnamesePdf() {
        ExportJob job = repository.save(new ExportJob(UUID.randomUUID(), USER_ID, CAT_ID, "CC-EXP-2026-000003",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D", List.of("SCAN_LOG", "PROFILE"), "vi",
                "Asia/Ho_Chi_Minh", NOW));

        generationService.generate(job.getId());

        assertThat(renderedHtml).singleElement().satisfies(html -> assertThat(html)
                .contains("<td>Mèo cái</td>")
                .contains("<td>3 tuổi 0 tháng (ước lượng)</td>")
                .contains("<td>04/10/2026 02:00</td>")
                .contains("● Trong khoảng tham chiếu")
                .contains("<td>Cao</td>"));
    }

    @Test
    @DisplayName("i18n — job bị từ chối vì quá tải ghi lý do theo locale của job")
    void rejectedJobReasonFollowsTheJobLocale() {
        ExportJob english = repository.save(new ExportJob(UUID.randomUUID(), USER_ID, CAT_ID, "CC-EXP-2026-000004",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D", List.of("TREND"), "en",
                "Asia/Ho_Chi_Minh", NOW));

        generationService.markRejected(english.getId(), NOW);

        assertThat(repository.findById(english.getId()).orElseThrow().getFailureReason())
                .isEqualTo("The system is busy. Please try creating the export again in a few minutes.");
    }

    private static final class RecordingScanQuery implements ScanHistoryQuery {

        final List<Instant[]> calls = new ArrayList<>();

        @Override
        public List<RecentScan> recentValidScans(UUID catId, Instant since, int limit) {
            return List.of();
        }

        @Override
        public List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to) {
            calls.add(new Instant[] {from, to});
            return List.of(new ExportScanRow(UUID.randomUUID(), NOW.minusSeconds(3600),
                    new BigDecimal("6.4"), "IN_RANGE", new BigDecimal("0.820"),
                    false, false, "NONE", List.of(), false));
        }
    }
}
