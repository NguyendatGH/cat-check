package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.privacy.api.AdminPolicyErrorCode;
import com.catcheck.privacy.domain.ConsentPurpose;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentPurposePort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L46–L48 — quản trị {@code policy_version} (p8 §8.4.12 mục (c), ô Q22).
 *
 * <p>Điều quan trọng nhất: <b>bản nháp không bao giờ được có hiệu lực</b>. Bảng không có cột
 * {@code status} (p4 B1) nên "nháp" = {@code published_by IS NULL} + {@code effective_from} ở
 * tương lai; nếu L47 nhận một ngày trong quá khứ thì bản vừa soạn trở thành bản hiện hành ngay
 * lúc INSERT và bước publish của DPO bị vòng qua hoàn toàn.</p>
 */
class PolicyVersionAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant NEXT_MONTH = NOW.plus(Duration.ofDays(30));

    private final FakePolicyPort policies = new FakePolicyPort();
    private final List<AuditEvent> audits = new ArrayList<>();
    private final PolicyVersionAdminService service = new PolicyVersionAdminService(
            policies, new FakePurposePort(), audits::add, new UuidV7(CLOCK), CLOCK);

    private static final PolicyVersionAdminService.PolicyAdminAction ACTION =
            new PolicyVersionAdminService.PolicyAdminAction(
                    UUID.fromString("00000000-0000-0000-0000-0000000000d1"), "DPO",
                    "Cap nhat chinh sach theo ND356", "req-1", "127.0.0.1", "curl");

    // ------------------------------------------------------------------ L47

    @Test
    void draftIsStoredUnpublishedAndReportedAsDraft() {
        PolicyVersionAdminService.AdminPolicyRow row = service.createDraft(
                PolicyType.PRIVACY, "2.0", "vi", "Chinh sach quyen rieng tu",
                "# noi dung moi", null, "Them muc dich moi", true,
                List.of("SERVICE_CORE"), NEXT_MONTH, ACTION);

        assertEquals(PolicyVersionAdminService.PolicyVersionStatus.DRAFT, row.status());
        assertNull(row.version().publishedBy(), "Ban nhap chua co nguoi publish");
        assertNotNull(row.version().contentHash(), "content_hash la bang chung Dieu 6.2 ND356");
        assertEquals(64, row.version().contentHash().length());
        assertEquals("ADMIN_POLICY_VERSION_DRAFTED", audits.getFirst().action());
        assertEquals("Cap nhat chinh sach theo ND356", audits.getFirst().metadata().get("reason"));
    }

    @Test
    void draftWithAnEffectiveDateInThePastIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.createDraft(
                PolicyType.PRIVACY, "2.0", "vi", "T", "# x", null, null, false,
                List.of(), NOW.minusSeconds(1), ACTION));

        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_INVALID, ex.errorCode());
        assertEquals(422, ex.errorCode().status().value());
        assertTrue(policies.store.isEmpty(), "Khong duoc INSERT gi khi bi tu choi");
    }

    @Test
    void reconsentWithoutAffectedPurposesIsRejected() {
        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                assertThrows(BusinessRuleException.class, () -> service.createDraft(
                        PolicyType.PRIVACY, "2.0", "vi", "T", "# x", null, null, true,
                        List.of(), NEXT_MONTH, ACTION)).errorCode(),
                "p15 REQ-VER-02: requires_reconsent = true bat buoc kem affected_purposes");
    }

    @Test
    void anUnknownAffectedPurposeIsRejected() {
        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                assertThrows(BusinessRuleException.class, () -> service.createDraft(
                        PolicyType.PRIVACY, "2.0", "vi", "T", "# x", null, null, true,
                        List.of("KHONG_CO_TRONG_CONSENT_PURPOSE"), NEXT_MONTH, ACTION)).errorCode(),
                "p4 B1: phan tu cua affected_purposes phai ton tai trong consent_purpose");
    }

    @Test
    void aNonSemverVersionIsRejected() {
        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                assertThrows(BusinessRuleException.class, () -> service.createDraft(
                        PolicyType.PRIVACY, "ban-moi", "vi", "T", "# x", null, null, false,
                        List.of(), NEXT_MONTH, ACTION)).errorCode());
    }

    @Test
    void aDuplicateTypeVersionLocaleIsAConflict() {
        service.createDraft(PolicyType.PRIVACY, "2.0", "vi", "T", "# x", null, null, false,
                List.of(), NEXT_MONTH, ACTION);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.createDraft(
                PolicyType.PRIVACY, "2.0", "vi", "T", "# y", null, null, false,
                List.of(), NEXT_MONTH, ACTION));

        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_EXISTS, ex.errorCode());
        assertEquals(409, ex.errorCode().status().value());
    }

    // ------------------------------------------------------------------ L48

    @Test
    void publishStampsThePublisherAndClosesThePreviousEffectiveVersion() {
        UUID oldId = policies.seedEffective(PolicyType.PRIVACY, "1.0", "vi");
        UUID draftId = service.createDraft(PolicyType.PRIVACY, "2.0", "vi", "T", "# x", null,
                "Them muc dich moi", true, List.of("SERVICE_CORE"), NEXT_MONTH, ACTION)
                .version().id();
        audits.clear();

        PolicyVersionAdminService.AdminPolicyRow published =
                service.publish(draftId, null, ACTION);

        assertEquals(ACTION.actorId(), published.version().publishedBy());
        assertEquals(PolicyVersionAdminService.PolicyVersionStatus.SCHEDULED, published.status(),
                "effective_from o tuong lai (p15 REQ-VER-06 doi bao truoc >= 7 ngay) => SCHEDULED");
        assertEquals(NEXT_MONTH, policies.store.get(oldId).effectiveTo(),
                "Ban cu phai bi dong dung vao ngay ban moi co hieu luc");
        AuditEvent event = audits.getFirst();
        assertEquals("ADMIN_POLICY_VERSION_PUBLISHED", event.action());
        assertEquals(true, event.metadata().get("requiresReconsent"));
        assertEquals(1, event.metadata().get("supersededCount"));
    }

    @Test
    void publishCanOverrideTheEffectiveDate() {
        UUID draftId = service.createDraft(PolicyType.TERMS, "1.1", "vi", "T", "# x", null, null,
                false, List.of(), NEXT_MONTH, ACTION).version().id();
        Instant later = NOW.plus(Duration.ofDays(60));

        PolicyVersionAdminService.AdminPolicyRow published = service.publish(draftId, later, ACTION);

        assertEquals(later, published.version().effectiveFrom());
    }

    @Test
    void publishingAnAlreadyPublishedVersionIsAConflict() {
        UUID draftId = service.createDraft(PolicyType.TERMS, "1.1", "vi", "T", "# x", null, null,
                false, List.of(), NEXT_MONTH, ACTION).version().id();
        service.publish(draftId, null, ACTION);

        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_ALREADY_PUBLISHED,
                assertThrows(ConflictException.class, () -> service.publish(draftId, null, ACTION))
                        .errorCode());
    }

    @Test
    void publishingAnUnknownIdIsNotFound() {
        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_NOT_FOUND,
                assertThrows(NotFoundException.class,
                        () -> service.publish(UUID.randomUUID(), null, ACTION)).errorCode());
    }

    // ------------------------------------------------------------------ L46

    @Test
    void listPagesAndDerivesStatusPerRow() {
        policies.seedEffective(PolicyType.PRIVACY, "1.0", "vi");
        service.createDraft(PolicyType.PRIVACY, "2.0", "vi", "T", "# x", null, null, false,
                List.of(), NEXT_MONTH, ACTION);

        PolicyVersionAdminService.AdminPolicyPage page = service.list("PRIVACY", "vi", 0, 20);

        assertEquals(2, page.totalElements());
        assertTrue(page.rows().stream().anyMatch(r ->
                r.status() == PolicyVersionAdminService.PolicyVersionStatus.EFFECTIVE));
        assertTrue(page.rows().stream().anyMatch(r ->
                r.status() == PolicyVersionAdminService.PolicyVersionStatus.DRAFT));
    }

    @Test
    void seededRowsWithoutAPublisherStillCountAsEffectiveNotDraft() {
        // R__seed_policy_version.sql ghi published_by = NULL va effective_from o qua khu.
        UUID seeded = policies.seedEffective(PolicyType.COOKIE, "1.0", "vi");

        assertEquals(PolicyVersionAdminService.PolicyVersionStatus.EFFECTIVE,
                PolicyVersionAdminService.statusOf(policies.store.get(seeded), NOW));
    }

    @Test
    void anUnknownPolicyTypeFilterIsRejected() {
        assertEquals(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                assertThrows(BusinessRuleException.class,
                        () -> service.list("KHONG_CO", "vi", 0, 20)).errorCode());
    }

    // ------------------------------------------------------------------ fixtures

    private static final class FakePolicyPort implements PolicyVersionPort {

        private final Map<UUID, PolicyVersion> store = new LinkedHashMap<>();

        private UUID seedEffective(PolicyType type, String version, String locale) {
            UUID id = UUID.randomUUID();
            store.put(id, new PolicyVersion(id, type, version, locale, "Seed", "# seed", null,
                    "a".repeat(64), null, false, List.of(),
                    NOW.minus(Duration.ofDays(300)), null, null, NOW.minus(Duration.ofDays(300))));
            return id;
        }

        @Override
        public void publish(PolicyVersion version) {
            store.put(version.id(), version);
        }

        @Override
        public Optional<PolicyVersion> findCurrent(PolicyType type, String locale, Instant now) {
            return store.values().stream()
                    .filter(v -> v.policyType() == type && v.locale().equals(locale) && v.isEffectiveAt(now))
                    .max(Comparator.comparing(PolicyVersion::effectiveFrom));
        }

        @Override
        public Optional<PolicyVersion> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<PolicyVersion> findAllByType(PolicyType type, String locale) {
            return store.values().stream()
                    .filter(v -> v.policyType() == type && v.locale().equals(locale))
                    .toList();
        }

        @Override
        public Optional<PolicyVersion> findByTypeAndVersion(PolicyType type, String version, String locale) {
            return store.values().stream()
                    .filter(v -> v.policyType() == type && v.version().equals(version)
                            && v.locale().equals(locale))
                    .findFirst();
        }

        @Override
        public List<PolicyVersion> findAllForAdmin(PolicyType type, String locale, int limit, int offset) {
            return matching(type, locale).stream().skip(offset).limit(limit).toList();
        }

        @Override
        public long countForAdmin(PolicyType type, String locale) {
            return matching(type, locale).size();
        }

        private List<PolicyVersion> matching(PolicyType type, String locale) {
            return store.values().stream()
                    .filter(v -> type == null || v.policyType() == type)
                    .filter(v -> locale == null || v.locale().equals(locale))
                    .toList();
        }

        @Override
        public int markPublished(UUID id, UUID publishedBy, Instant effectiveFrom) {
            PolicyVersion current = store.get(id);
            if (current == null || current.publishedBy() != null) {
                return 0;
            }
            store.put(id, copy(current, effectiveFrom, current.effectiveTo(), publishedBy));
            return 1;
        }

        @Override
        public int closeEffective(PolicyType type, String locale, Instant effectiveTo, UUID exceptId) {
            int closed = 0;
            for (Map.Entry<UUID, PolicyVersion> entry : Map.copyOf(store).entrySet()) {
                PolicyVersion v = entry.getValue();
                boolean candidate = v.policyType() == type && v.locale().equals(locale)
                        && !entry.getKey().equals(exceptId)
                        && v.effectiveTo() == null
                        && !v.effectiveFrom().isAfter(effectiveTo);
                if (candidate) {
                    store.put(entry.getKey(), copy(v, v.effectiveFrom(), effectiveTo, v.publishedBy()));
                    closed++;
                }
            }
            return closed;
        }

        private static PolicyVersion copy(PolicyVersion v, Instant from, Instant to, UUID publishedBy) {
            return new PolicyVersion(v.id(), v.policyType(), v.version(), v.locale(), v.title(),
                    v.contentMd(), v.contentUrl(), v.contentHash(), v.summaryOfChanges(),
                    v.requiresReconsent(), v.affectedPurposes(), from, to, publishedBy, v.createdAt());
        }
    }

    /** Chỉ cần một purpose hợp lệ để kiểm nhánh "purpose lạ bị từ chối". */
    private static final class FakePurposePort implements ConsentPurposePort {

        @Override
        public List<ConsentPurpose> findAllActive() {
            return List.of(new ConsentPurpose("SERVICE_CORE", "Dich vu loi", null,
                    "Du lieu can de chay dich vu", null, true, false, true, 1, 1, true,
                    "Khong the rut", NOW));
        }

        @Override
        public Optional<ConsentPurpose> findByCode(String purposeCode) {
            return findAllActive().stream().filter(p -> p.code().equals(purposeCode)).findFirst();
        }
    }
}
