package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.StorageKey;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.api.ScanReassignedEvent;
import com.catcheck.scan.application.spi.OpenDsarPort;
import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanImage;
import com.catcheck.scan.domain.ScanImageStorageProvider;
import com.catcheck.scan.domain.ScanReassignment;
import com.catcheck.scan.domain.ScanStatus;
import com.catcheck.scan.domain.StoreImageReason;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanQueryRepository;
import com.catcheck.scan.domain.port.ScanReassignmentRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L5 ({@code GET /admin/users/{userId}/scans}), L6 ({@code GET /admin/scans/{scanId}/image}) và
 * L18 ({@code POST /admin/scans/{scanId}/reassign-cat}) — p8 §8.4.12 mục (a).
 *
 * <p>Tính chất quan trọng nhất ở đây là <b>điều kiện DSAR</b>: nó là thứ duy nhất đứng giữa một
 * tài khoản DPO và ảnh vệ sinh của mèo người khác (p14 ô Q5), nên nó được neo ở cả hai hướng —
 * có DSAR thì mở được, không có thì 403 <i>và</i> có một dòng {@code audit_log} {@code DENIED}.</p>
 */
class AdminScanServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID OTHER_USER = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final UUID SCAN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000d1");
    private static final UUID CAT_A = UUID.fromString("00000000-0000-7000-8000-0000000000c1");
    private static final UUID CAT_B = UUID.fromString("00000000-0000-7000-8000-0000000000c2");
    private static final Instant NOW = Instant.parse("2026-10-06T09:00:00Z");

    /** Quét từ 10 ngày trước ⇒ cửa sổ 24 giờ của người dùng đã đóng từ lâu (p6 §6.10.3). */
    private static final Instant CAPTURED = NOW.minusSeconds(10L * 86_400);

    private FakeScanRepository scanRepository;
    private FakeScanQueryRepository queryRepository;
    private FakeReassignmentRepository reassignmentRepository;
    private FakeScanImageRepository imageRepository;
    private FakeCatOwnershipPort catOwnershipPort;
    private FakeOpenDsarPort openDsarPort;
    private RecordingAuditLog auditLog;
    private RecordingEventPublisher eventPublisher;
    private ImageStorage imageStorage;
    private AdminScanService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scanRepository = new FakeScanRepository();
        queryRepository = new FakeScanQueryRepository();
        reassignmentRepository = new FakeReassignmentRepository();
        imageRepository = new FakeScanImageRepository();
        catOwnershipPort = new FakeCatOwnershipPort();
        openDsarPort = new FakeOpenDsarPort();
        auditLog = new RecordingAuditLog();
        eventPublisher = new RecordingEventPublisher();
        imageStorage = mock(ImageStorage.class);

        ScanImageAccessService imageAccessService = new ScanImageAccessService(
                scanRepository, imageRepository, imageStorage, clock);
        service = new AdminScanService(
                scanRepository, queryRepository, reassignmentRepository, imageAccessService,
                catOwnershipPort, openDsarPort, auditLog, eventPublisher, new UuidV7(clock), clock);
    }

    /* ------------------------------------------------------------------ L5 */

    @Test
    @DisplayName("L5: ADMIN_SUPPORT ⇒ fullData = false (dữ liệu che) và vẫn ghi audit dù chỉ đọc")
    void supportSeesMaskedData() {
        queryRepository.rows.add(row(SCAN_ID, CAT_A));

        AdminScanService.AdminScanPage page =
                service.listUserScans(USER_ID, 0, 20, context(Set.of("ADMIN_SUPPORT")));

        assertThat(page.fullData()).isFalse();
        assertThat(page.rows()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(auditLog.actions()).containsExactly("ADMIN_USER_SCANS_VIEW");
    }

    @Test
    @DisplayName("L5: DPO nhưng KHÔNG có DSAR mở ⇒ vẫn là dữ liệu che (p8 L4/L5: 'khi có DSAR mở')")
    void dpoWithoutOpenDsarStillSeesMaskedData() {
        queryRepository.rows.add(row(SCAN_ID, CAT_A));

        AdminScanService.AdminScanPage page =
                service.listUserScans(USER_ID, 0, 20, context(Set.of("DPO")));

        assertThat(page.fullData()).isFalse();
    }

    @Test
    @DisplayName("L5: DPO + có DSAR mở ⇒ fullData = true, audit ghi rõ unmaskedByDsar")
    void dpoWithOpenDsarSeesFullData() {
        openDsarPort.openFor.add(USER_ID);
        queryRepository.rows.add(row(SCAN_ID, CAT_A));

        AdminScanService.AdminScanPage page =
                service.listUserScans(USER_ID, 0, 20, context(Set.of("DPO")));

        assertThat(page.fullData()).isTrue();
        assertThat(auditLog.events.getFirst().metadata()).containsEntry("unmaskedByDsar", true);
    }

    @Test
    @DisplayName("L5: size vượt trần bị kẹp về 100 và page âm về 0 (p8 §8.1.4)")
    void listClampsPaging() {
        AdminScanService.AdminScanPage page =
                service.listUserScans(USER_ID, -5, 5_000, context(Set.of("ADMIN_SUPER")));

        assertThat(page.size()).isEqualTo(AdminScanService.MAX_PAGE_SIZE);
        assertThat(page.page()).isZero();
    }

    /* ------------------------------------------------------------------ L6 */

    @Test
    @DisplayName("L6: không có dsar_request mở ⇒ 403 SCAN_IMAGE_DSAR_REQUIRED + audit DENIED")
    void imageDeniedWithoutOpenDsar() {
        scanRepository.save(analyzedScan(true));

        assertThatThrownBy(() -> service.openImageForDpo(SCAN_ID, context(Set.of("DPO"))))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(ScanErrorCode.SCAN_IMAGE_DSAR_REQUIRED);

        assertThat(auditLog.events).hasSize(1);
        assertThat(auditLog.events.getFirst().action()).isEqualTo("ADMIN_SCAN_IMAGE_VIEW");
        assertThat(auditLog.events.getFirst().outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(auditLog.events.getFirst().metadata())
                .containsEntry("denyReason", "NO_OPEN_DSAR")
                // UUID dạng ĐỐI TƯỢNG, không phải chuỗi — `PiiRedactor` xoá mọi chuỗi ≥ 32 ký tự
                // [A-Za-z0-9+/=_-] (một UUID khớp đúng lớp đó) khỏi metadata. Hồi quy cho H15.156.
                .containsEntry("scanId", SCAN_ID);
    }

    @Test
    @DisplayName("L6: ảnh không lưu ⇒ vẫn ghi MỘT dòng audit outcome = ERROR, rồi ném tiếp 404")
    void imageNotStoredStillAudits() {
        scanRepository.save(analyzedScan(false));
        openDsarPort.openFor.add(USER_ID);

        assertThatThrownBy(() -> service.openImageForDpo(SCAN_ID, context(Set.of("DPO"))))
                .isInstanceOf(NotFoundException.class);

        assertThat(auditLog.events).hasSize(1);
        assertThat(auditLog.events.getFirst().outcome()).isEqualTo(AuditOutcome.ERROR);
        assertThat(auditLog.events.getFirst().metadata())
                .containsEntry("denyReason", "SCAN_IMAGE_NOT_STORED");
    }

    @Test
    @DisplayName("L6: có dsar_request mở ⇒ mở được ảnh, audit SUCCESS")
    void imageOpensWithOpenDsar() {
        scanRepository.save(analyzedScan(true));
        imageRepository.save(storedImage(NOW.plusSeconds(86_400)));
        openDsarPort.openFor.add(USER_ID);
        when(imageStorage.open(any(StorageKey.class)))
                .thenReturn(Optional.of(new ByteArrayInputStream(new byte[] {1, 2, 3})));

        ScanImageAccessService.Result result = service.openImageForDpo(SCAN_ID, context(Set.of("DPO")));

        assertThat(result).isInstanceOf(ScanImageAccessService.Stream.class);
        assertThat(auditLog.events.getFirst().outcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    @DisplayName("L6: scan không tồn tại ⇒ 404 SCAN_NOT_FOUND và KHÔNG tra DSAR (không dò id)")
    void imageMissingScanIs404() {
        assertThatThrownBy(() -> service.openImageForDpo(SCAN_ID, context(Set.of("DPO"))))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(ScanErrorCode.SCAN_NOT_FOUND);
        assertThat(openDsarPort.queried).isEmpty();
    }

    @Test
    @DisplayName("L6: store_image = false ⇒ 404 SCAN_IMAGE_NOT_STORED dù có DSAR mở")
    void imageNotStoredIs404EvenWithDsar() {
        scanRepository.save(analyzedScan(false));
        openDsarPort.openFor.add(USER_ID);

        assertThatThrownBy(() -> service.openImageForDpo(SCAN_ID, context(Set.of("DPO"))))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(ScanErrorCode.SCAN_IMAGE_NOT_STORED);
    }

    /* ----------------------------------------------------------------- L18 */

    @Test
    @DisplayName("L18: đổi mèo SAU cửa sổ 24 giờ vẫn được — ghi scan_reassignment với vai trò admin + reason")
    void reassignWorksAfterUserWindowClosed() {
        scanRepository.save(analyzedScan(true));
        catOwnershipPort.cats.put(CAT_B, new CatOwnershipPort.CatSnapshot(CAT_B, USER_ID, "Mun", false, false));

        AdminScanService.AdminReassignResult result = service.reassign(
                SCAN_ID, CAT_B, false, context(Set.of("ADMIN_SUPPORT")));

        assertThat(result.fromCatId()).isEqualTo(CAT_A);
        assertThat(result.toCatId()).isEqualTo(CAT_B);
        assertThat(result.toAssignment()).isEqualTo("ASSIGNED");
        assertThat(scanRepository.byId(SCAN_ID).getCatId()).isEqualTo(CAT_B);

        ScanReassignment logged = reassignmentRepository.rows.getFirst();
        assertThat(logged.getChangedBy()).isEqualTo(ADMIN_ID);
        assertThat(logged.getChangedByRole()).isEqualTo("ADMIN_SUPER");
        // ck_scan_reassignment: reason BẮT BUỘC NOT NULL khi người đổi không phải USER (p4 D13).
        assertThat(logged.getReason()).isNotBlank();

        assertThat(eventPublisher.events).hasSize(1);
        assertThat(auditLog.actions()).containsExactly("ADMIN_SCAN_REASSIGNED");
        // before/after mang UUID dạng ĐỐI TƯỢNG để `PiiRedactor` không xoá id (H15.156).
        assertThat(auditLog.events.getFirst().before()).containsEntry("catId", CAT_A);
        assertThat(auditLog.events.getFirst().after()).containsEntry("catId", CAT_B);
    }

    @Test
    @DisplayName("L18: đổi lần thứ 4 vẫn được — trần 3 lần của p6 §6.10.3 không áp lên đường admin (H15.152)")
    void reassignIsNotCappedForAdmin() {
        Scan scan = analyzedScan(true);
        scan.reassignTo(CAT_B, ScanAssignment.ASSIGNED, CAPTURED);
        scan.reassignTo(CAT_A, ScanAssignment.ASSIGNED, CAPTURED);
        scan.reassignTo(CAT_B, ScanAssignment.ASSIGNED, CAPTURED);
        scanRepository.save(scan);
        catOwnershipPort.cats.put(CAT_A, new CatOwnershipPort.CatSnapshot(CAT_A, USER_ID, "Luna", false, false));

        AdminScanService.AdminReassignResult result =
                service.reassign(SCAN_ID, CAT_A, false, context(Set.of("ADMIN_SUPPORT")));

        assertThat(result.reassignCount()).isEqualTo((short) 4);
    }

    @Test
    @DisplayName("L18: mèo đích thuộc NGƯỜI KHÁC ⇒ 404 CAT_NOT_FOUND, không chuyển dữ liệu sang tài khoản khác")
    void reassignRejectsCatOfAnotherOwner() {
        scanRepository.save(analyzedScan(true));
        catOwnershipPort.cats.put(CAT_B, new CatOwnershipPort.CatSnapshot(CAT_B, OTHER_USER, "Bé lạ", false, false));

        // 404 chứ không 403: p8 §8.2.5 mục 1. `ScanErrorCode.CAT_NOT_OWNED` mang status 403 ngay
        // trên ErrorCode (ngoại lệ dành riêng cho POST /scans), nên dùng nó ở đây sẽ trả 403 —
        // đo thật bằng curl trước khi đổi sang CatErrorCode.CAT_NOT_FOUND.
        assertThatThrownBy(() -> service.reassign(SCAN_ID, CAT_B, false, context(Set.of("ADMIN_SUPER"))))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(com.catcheck.cat.api.CatErrorCode.CAT_NOT_FOUND);
        assertThat(scanRepository.byId(SCAN_ID).getCatId()).isEqualTo(CAT_A);
        assertThat(reassignmentRepository.rows).isEmpty();
    }

    @Test
    @DisplayName("L18: chuyển sang SHARED_UNKNOWN ⇒ cat_id về null, không cần mèo đích")
    void reassignToSharedUnknown() {
        scanRepository.save(analyzedScan(true));

        AdminScanService.AdminReassignResult result =
                service.reassign(SCAN_ID, null, true, context(Set.of("ADMIN_SUPPORT")));

        assertThat(result.toCatId()).isNull();
        assertThat(result.toAssignment()).isEqualTo("SHARED_UNKNOWN");
        assertThat(scanRepository.byId(SCAN_ID).getCatId()).isNull();
    }

    @Test
    @DisplayName("L18: scan đã xoá mềm ⇒ 409 SCAN_NOT_REASSIGNABLE")
    void reassignRejectsDeletedScan() {
        Scan scan = analyzedScan(true);
        scan.softDelete(NOW);
        scanRepository.save(scan);

        assertThatThrownBy(() -> service.reassign(SCAN_ID, CAT_B, false, context(Set.of("ADMIN_SUPER"))))
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(ScanErrorCode.SCAN_NOT_REASSIGNABLE);
    }

    /* --------------------------------------------------------------- helper */

    private static AdminActionContext context(Set<String> roles) {
        return new AdminActionContext(ADMIN_ID, "ADMIN_SUPER", roles,
                "Khách báo gán nhầm bé, ticket #771", "req-1", "203.0.113.9", "junit");
    }

    private static Scan analyzedScan(boolean storeImage) {
        Scan scan = new Scan(SCAN_ID, USER_ID, CAT_A, ScanAssignment.ASSIGNED, CAPTURED,
                CaptureSource.CAMERA, "junit", false, storeImage,
                storeImage ? null : StoreImageReason.TRIAL, ScanStatus.ANALYZED, null, null, CAPTURED);
        scan.markAnalyzed(UUID.fromString("00000000-0000-7000-8000-0000000000e1"), null, CAPTURED);
        return scan;
    }

    private static ScanImage storedImage(Instant expiresAt) {
        return new ScanImage(UUID.fromString("00000000-0000-7000-8000-0000000000f1"), SCAN_ID,
                ScanImageStorageProvider.LOCAL, "scan/abc.jpg", "image/jpeg", 1_024, 800, 600,
                "deadbeef", expiresAt, CAPTURED);
    }

    private static ScanQueryRepository.Row row(UUID scanId, UUID catId) {
        return new ScanQueryRepository.Row(
                scanId, catId, USER_ID, "ASSIGNED", CAPTURED, "ANALYZED",
                new BigDecimal("6.40"), new BigDecimal("6.30"), new BigDecimal("6.50"),
                "NORMAL", null, new BigDecimal("0.82"), false,
                new BigDecimal("70.1"), new BigDecimal("2.3"), new BigDecimal("14.7"),
                88, "CHART_CARD", null, null, false, "engine-1.0.0",
                List.of(), true, false, true, CAPTURED.plusSeconds(1_209_600), null,
                null, "ghi chú tranh chấp", (short) 0, 120, "req-0");
    }

    /* ----------------------------------------------------------------- fake */

    private static final class FakeScanRepository implements ScanRepository {

        private final Map<UUID, Scan> rows = new HashMap<>();

        @Override
        public Scan save(Scan scan) {
            rows.put(scan.getId(), scan);
            return scan;
        }

        @Override
        public Optional<Scan> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public void flushPendingWrites() {
            // Fake trong bộ nhớ: không có gì chờ xuống DB.
        }

        Scan byId(UUID id) {
            return rows.get(id);
        }
    }

    private static final class FakeScanQueryRepository implements ScanQueryRepository {

        private final List<Row> rows = new ArrayList<>();

        @Override
        public Page findHistory(HistoryFilter filter, String cursor, int limit) {
            throw new UnsupportedOperationException("L5 dùng đường offset");
        }

        @Override
        public Summary summarize(HistoryFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RuleRow> findRecentForRules(UUID catId, Instant since, int limit) {
            return List.of();
        }

        @Override
        public Optional<Row> findRowByScanId(UUID scanId) {
            return rows.stream().filter(row -> row.scanId().equals(scanId)).findFirst();
        }

        @Override
        public OffsetPage findByUserForAdmin(UUID userId, int offset, int limit) {
            List<Row> page = rows.stream()
                    .filter(row -> row.userId().equals(userId))
                    .skip(offset)
                    .limit(limit)
                    .toList();
            return new OffsetPage(page, rows.size());
        }

        @Override
        public List<Row> findForExport(UUID catId, Instant from, Instant to) {
            return List.of();
        }
    }

    private static final class FakeScanImageRepository
            implements com.catcheck.scan.domain.port.ScanImageRepository {

        private final Map<UUID, ScanImage> rows = new HashMap<>();

        @Override
        public ScanImage save(ScanImage image) {
            rows.put(image.getScanId(), image);
            return image;
        }

        @Override
        public Optional<ScanImage> findByScanId(UUID scanId) {
            return Optional.ofNullable(rows.get(scanId));
        }
    }

    private static final class FakeReassignmentRepository implements ScanReassignmentRepository {

        private final List<ScanReassignment> rows = new ArrayList<>();

        @Override
        public ScanReassignment save(ScanReassignment reassignment) {
            rows.add(reassignment);
            return reassignment;
        }

        @Override
        public List<ScanReassignment> findByScanIdOrderByChangedAtDesc(UUID scanId) {
            return List.copyOf(rows);
        }

        @Override
        public long countByScanId(UUID scanId) {
            return rows.size();
        }
    }

    private static final class FakeCatOwnershipPort implements CatOwnershipPort {

        private final Map<UUID, CatSnapshot> cats = new HashMap<>();

        @Override
        public Optional<CatSnapshot> findSnapshot(UUID catId) {
            return Optional.ofNullable(cats.get(catId));
        }
    }

    private static final class FakeOpenDsarPort implements OpenDsarPort {

        private final Set<UUID> openFor = new HashSet<>();
        private final List<UUID> queried = new ArrayList<>();

        @Override
        public boolean hasOpenRequestFor(UUID userId) {
            queried.add(userId);
            return openFor.contains(userId);
        }
    }

    private static final class RecordingAuditLog implements AuditLogService {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }

        List<String> actions() {
            return events.stream().map(AuditEvent::action).toList();
        }
    }

    private static final class RecordingEventPublisher
            implements org.springframework.context.ApplicationEventPublisher {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            if (event instanceof ScanReassignedEvent) {
                events.add(event);
            }
        }
    }
}
