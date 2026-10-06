package com.catcheck.insight.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.insight.api.InsightErrorCode;
import com.catcheck.insight.domain.HealthFlagSeverity;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.MonitoringRuleCode;
import com.catcheck.insight.domain.MonitoringRuleFixtures;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L38–L39 (p8 §8.4.12 mục (c)).
 *
 * <p>Hai bất biến của p4 D11 là trọng tâm: {@code cooldown_hours >= 0} và <b>{@code 0} chỉ cho
 * {@code URGENT_CLINICAL_SIGN}</b>. Để một rule khác về 0 nghĩa là mỗi lần quét đều bắn một
 * flag — người dùng tắt thông báo và mất luôn cảnh báo thật.</p>
 */
class MonitoringRuleAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant SEEDED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeRuleRepository rules = new FakeRuleRepository();
    private final List<AuditEvent> audits = new ArrayList<>();
    private final MonitoringRuleAdminService service =
            new MonitoringRuleAdminService(rules, audits::add, CLOCK);

    private static final MonitoringRuleAdminService.RuleAdminAction ACTION =
            new MonitoringRuleAdminService.RuleAdminAction(
                    UUID.randomUUID(), "Tat rule vi dang hieu chuan lai bang mau",
                    "req-1", "127.0.0.1", "curl");

    @Test
    void listForAdminIncludesDisabledRules() {
        rules.put(MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name(), 72, false);

        assertEquals(1, service.listForAdmin().size(),
                "Man cau hinh phai thay duoc rule minh vua tat");
    }

    @Test
    void unknownRuleCodeIsNotFound() {
        assertEquals(InsightErrorCode.MONITORING_RULE_NOT_FOUND,
                assertThrows(NotFoundException.class, () -> service.require("KHONG_TON_TAI")).errorCode());
    }

    @Test
    void updateChangesEnabledParamsAndCooldownAndWritesBeforeAfterAudit() {
        rules.put(MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name(), 72, true);

        MonitoringRule updated = service.update(
                MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name(),
                false,
                Map.of("windowHours", 48, "minCount", 3),
                24,
                false,
                ACTION);

        assertFalse(updated.isEnabled());
        assertEquals(24, updated.getCooldownHours());
        assertEquals(48, updated.paramAsInt("windowHours", 0));
        assertEquals(NOW, updated.getUpdatedAt(), "updated_at phai doi de ETag cu khong con khop");
        AuditEvent event = audits.getFirst();
        assertEquals("ADMIN_MONITORING_RULE_UPDATED", event.action());
        assertEquals(true, event.before().get("enabled"));
        assertEquals(false, event.after().get("enabled"));
        assertEquals(72, event.before().get("cooldownHours"));
        assertEquals(24, event.after().get("cooldownHours"));
        assertEquals("Tat rule vi dang hieu chuan lai bang mau", event.metadata().get("reason"));
    }

    @Test
    void absentFieldsLeaveTheRuleUnchanged() {
        rules.put(MonitoringRuleCode.BASELINE_DEVIATION.name(), 72, true);

        MonitoringRule updated = service.update(
                MonitoringRuleCode.BASELINE_DEVIATION.name(), null, null, null, null, ACTION);

        assertTrue(updated.isEnabled(), "merge-patch: vang field = khong doi (p8 §8.1.11)");
        assertEquals(72, updated.getCooldownHours());
    }

    @Test
    void zeroCooldownIsRejectedForEveryRuleExceptUrgentClinicalSign() {
        rules.put(MonitoringRuleCode.MONOTONIC_TREND.name(), 72, true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.update(
                MonitoringRuleCode.MONOTONIC_TREND.name(), null, null, 0, null, ACTION));

        assertEquals(InsightErrorCode.MONITORING_RULE_INVALID, ex.errorCode());
        assertEquals(422, ex.errorCode().status().value());
        assertTrue(audits.isEmpty(), "Khong duoc ghi audit cho mot thay doi bi tu choi");
    }

    @Test
    void zeroCooldownIsAcceptedForUrgentClinicalSign() {
        rules.put(MonitoringRuleCode.URGENT_CLINICAL_SIGN.name(), 0, true);

        MonitoringRule updated = service.update(
                MonitoringRuleCode.URGENT_CLINICAL_SIGN.name(), null, null, 0, null, ACTION);

        assertEquals(0, updated.getCooldownHours(), "p4 D11 cho phep 0 dung o rule nay");
    }

    @Test
    void emptyOrNestedParamsAreRejected() {
        rules.put(MonitoringRuleCode.LOW_QUALITY_STREAK.name(), 24, true);
        String code = MonitoringRuleCode.LOW_QUALITY_STREAK.name();

        assertEquals(InsightErrorCode.MONITORING_RULE_INVALID,
                assertThrows(BusinessRuleException.class,
                        () -> service.update(code, null, Map.of(), null, null, ACTION)).errorCode());
        assertEquals(InsightErrorCode.MONITORING_RULE_INVALID,
                assertThrows(BusinessRuleException.class,
                        () -> service.update(code, null, Map.of("nested", Map.of("a", 1)), null, null, ACTION))
                        .errorCode(),
                "Map long ghi duoc vao JSONB nhung paramAsInt doc ra gia tri mac dinh — rule chay sai lang le");
    }

    /**
     * Bug thật đã sửa: trigger {@code trg_monitoring_rule_updated_at} ghi {@code updated_at} ở
     * phía DB, nên nếu service trả về entity trong bộ nhớ thì {@code ETag} của response lệch với
     * dòng đã lưu và lần {@code PATCH} kế tiếp luôn {@code 412 RESOURCE_MODIFIED}. Test này ép
     * cổng trả về một giá trị KHÁC để chứng minh service dùng bản đã đọc lại, không dùng bản
     * trong bộ nhớ.
     */
    @Test
    void theRuleReturnedComesFromTheReloadNotFromTheInMemoryEntity() {
        rules.put(MonitoringRuleCode.BASELINE_DEVIATION.name(), 72, true);
        Instant triggerWroteThis = Instant.parse("2026-10-06T10:00:00.123456Z");
        rules.reloadUpdatedAt = triggerWroteThis;

        MonitoringRule updated = service.update(
                MonitoringRuleCode.BASELINE_DEVIATION.name(), null, null, 48, null, ACTION);

        assertEquals(triggerWroteThis, updated.getUpdatedAt(),
                "ETag phai tinh tu gia tri DB da ghi, khong tu Clock cua ung dung");
    }

    // ------------------------------------------------------------------ fixtures

    private final class FakeRuleRepository implements MonitoringRuleRepository {

        private final Map<String, MonitoringRule> store = new LinkedHashMap<>();

        /** Giá trị mà "DB" trả về cho {@code updated_at} sau khi trigger chạy; null = giữ nguyên. */
        private Instant reloadUpdatedAt;

        private void put(String code, int cooldownHours, boolean enabled) {
            MonitoringRule rule = MonitoringRuleFixtures.rule(code,
                    Map.of("windowHours", 72, "minCount", 2),
                    HealthFlagSeverity.ATTENTION, cooldownHours, "insight.flag." + code, SEEDED_AT);
            if (!enabled) {
                rule.applyAdminUpdate(false, null, null, null, SEEDED_AT);
            }
            store.put(code, rule);
        }

        @Override
        public List<MonitoringRule> findAllEnabled() {
            return store.values().stream().filter(MonitoringRule::isEnabled).toList();
        }

        @Override
        public List<MonitoringRule> findAllEnabledForDisplay() {
            return findAllEnabled();
        }

        @Override
        public Optional<MonitoringRule> findByCode(String code) {
            return Optional.ofNullable(store.get(code));
        }

        @Override
        public List<MonitoringRule> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public List<MonitoringRule> findAllForAdmin() {
            return List.copyOf(store.values());
        }

        @Override
        public MonitoringRule saveAndReload(MonitoringRule rule) {
            if (reloadUpdatedAt != null) {
                rule.applyAdminUpdate(null, null, null, null, reloadUpdatedAt);
            }
            store.put(rule.getCode(), rule);
            return rule;
        }
    }
}
