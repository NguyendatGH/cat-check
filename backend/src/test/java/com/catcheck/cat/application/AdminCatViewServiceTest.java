package com.catcheck.cat.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.cat.application.spi.OpenDsarPort;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.port.CatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 — {@code GET /admin/users/{userId}/cats} (p8 §8.4.12 mục (a), p14 ô Q3).
 *
 * <p>Điều được neo: hồ sơ mèo ra khỏi service ở dạng <b>che</b> cho Super/Support, và chỉ mở đầy
 * đủ khi người gọi là {@code DPO} <b>và</b> người dùng đang có {@code dsar_request} mở — cả hai
 * điều kiện, không phải một. Cộng với việc một endpoint chỉ đọc vẫn ghi {@code audit_log} (cột
 * {@code Aud} của p8 L4, p15 REQ-AUD-04).</p>
 */
class AdminCatViewServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final Instant NOW = Instant.parse("2026-10-06T09:00:00Z");

    private FakeCatRepository catRepository;
    private FakeOpenDsarPort openDsarPort;
    private RecordingAuditLog auditLog;
    private AdminCatViewService service;

    @BeforeEach
    void setUp() {
        catRepository = new FakeCatRepository();
        openDsarPort = new FakeOpenDsarPort();
        auditLog = new RecordingAuditLog();
        service = new AdminCatViewService(catRepository, openDsarPort, auditLog);
    }

    @Test
    @DisplayName("L4: ADMIN_SUPPORT ⇒ fullData = false, và endpoint chỉ đọc vẫn ghi audit_log")
    void supportSeesMaskedListingAndAuditIsWritten() {
        catRepository.rows.add(cat("Luna"));

        AdminCatViewService.AdminCatListing listing =
                service.listCatsOf(USER_ID, context(Set.of("ADMIN_SUPPORT")));

        assertThat(listing.fullData()).isFalse();
        assertThat(listing.cats()).hasSize(1);
        assertThat(auditLog.actions()).containsExactly("ADMIN_USER_CATS_VIEW");
        assertThat(auditLog.events.getFirst().metadata())
                .containsEntry("unmaskedByDsar", false)
                .containsEntry("catCount", 1);
    }

    @Test
    @DisplayName("L4: DPO nhưng KHÔNG có DSAR mở ⇒ vẫn che (p8 L4: 'DPO thấy đầy đủ KHI có DSAR mở')")
    void dpoWithoutOpenDsarStillMasked() {
        catRepository.rows.add(cat("Luna"));

        AdminCatViewService.AdminCatListing listing =
                service.listCatsOf(USER_ID, context(Set.of("DPO")));

        assertThat(listing.fullData()).isFalse();
    }

    @Test
    @DisplayName("L4: DPO + DSAR mở ⇒ fullData = true")
    void dpoWithOpenDsarSeesFullData() {
        openDsarPort.openFor.add(USER_ID);
        catRepository.rows.add(cat("Luna"));

        AdminCatViewService.AdminCatListing listing =
                service.listCatsOf(USER_ID, context(Set.of("DPO")));

        assertThat(listing.fullData()).isTrue();
    }

    @Test
    @DisplayName("L4: không phải DPO thì KHÔNG truy vấn dsar_request (một câu SQL vô ích mỗi lần mở màn)")
    void nonDpoDoesNotQueryDsar() {
        service.listCatsOf(USER_ID, context(Set.of("ADMIN_SUPER")));

        assertThat(openDsarPort.queried).isEmpty();
    }

    @Test
    @DisplayName("L4: lấy CẢ ACTIVE lẫn ARCHIVED — truyền null làm JPQL thật chỉ trả ARCHIVED")
    void bothActiveAndArchivedProfilesAreIncluded() {
        Cat active = cat("Luna");
        Cat archived = cat("Mun");
        archived.archive(NOW);
        catRepository.rows.add(active);
        catRepository.rows.add(archived);

        AdminCatViewService.AdminCatListing listing =
                service.listCatsOf(USER_ID, context(Set.of("ADMIN_SUPPORT")));

        // Hồi quy cho bug đo được trên app thật: statusFilter = null ⇒ danh sách rỗng.
        assertThat(listing.cats()).hasSize(2);
        assertThat(listing.cats()).extracting(Cat::getStatus)
                .containsExactlyInAnyOrder(CatStatus.ACTIVE, CatStatus.ARCHIVED);
        assertThat(catRepository.includeArchivedAsked).isTrue();
    }

    @Test
    @DisplayName("AdminFreeTextMask: giữ đúng 1 ký tự đầu; chuỗi 1 ký tự che hết; null vẫn là null")
    void freeTextMaskKeepsOneCharacter() {
        assertThat(AdminFreeTextMask.text("Luna")).isEqualTo("L***");
        assertThat(AdminFreeTextMask.text("  Mun  ")).isEqualTo("M***");
        assertThat(AdminFreeTextMask.text("L")).isEqualTo("***");
        assertThat(AdminFreeTextMask.text("")).isEqualTo("***");
        // Vắng dữ liệu và che dữ liệu là HAI trạng thái khác nhau — client phải phân biệt được.
        assertThat(AdminFreeTextMask.text(null)).isNull();
    }

    /* --------------------------------------------------------------- helper */

    private static AdminActionContext context(Set<String> roles) {
        return new AdminActionContext(ADMIN_ID, roles.iterator().next(), roles,
                "Khách báo sai tên bé, ticket #902", "req-1", "203.0.113.9", "junit");
    }

    private static Cat cat(String name) {
        return Cat.create(UUID.randomUUID(), USER_ID, name, LocalDate.of(2023, 5, 1), null,
                "CC-VN-A1B2C3", LocalDate.of(2026, 10, 6), NOW);
    }

    /* ----------------------------------------------------------------- fake */

    private static final class FakeCatRepository implements CatRepository {

        private final List<Cat> rows = new ArrayList<>();
        private boolean includeArchivedAsked;

        @Override
        public Optional<Cat> findByIdAndOwnerId(UUID id, UUID ownerId) {
            return rows.stream()
                    .filter(cat -> cat.getId().equals(id) && cat.getOwnerId().equals(ownerId))
                    .findFirst();
        }

        /**
         * Chép ĐÚNG mệnh đề WHERE của JPQL thật:
         * {@code c.status = :statusFilter OR (:includeArchived = true AND c.status = ARCHIVED)}.
         *
         * <p>Đặc biệt giữ nguyên hành vi của {@code statusFilter = null}: trong SQL ba giá trị
         * {@code c.status = null} KHÔNG bao giờ đúng, nên nhánh đầu tắt hẳn. Một fake "null nghĩa
         * là mọi trạng thái" sẽ xanh trong khi endpoint thật trả danh sách rỗng — đúng con bug đã
         * đo được trên app chạy thật.</p>
         */
        @Override
        public List<Cat> findAllByOwnerId(UUID ownerId, CatStatus statusFilter, boolean includeArchived) {
            this.includeArchivedAsked = includeArchived;
            return rows.stream()
                    .filter(cat -> cat.getOwnerId().equals(ownerId))
                    .filter(cat -> cat.getStatus() == statusFilter
                            || (includeArchived && cat.getStatus() == CatStatus.ARCHIVED))
                    .toList();
        }

        @Override
        public long countActiveByOwnerId(UUID ownerId) {
            return rows.size();
        }

        @Override
        public Optional<Cat> findPrimaryByOwnerId(UUID ownerId) {
            return Optional.empty();
        }

        @Override
        public void clearPrimaryForOwner(UUID ownerId, Instant now) {
            // không dùng ở L4
        }

        @Override
        public boolean existsByPublicCode(String publicCode) {
            return false;
        }

        @Override
        public Cat save(Cat cat) {
            rows.add(cat);
            return cat;
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
}
