package com.catcheck.admin.application;

import com.catcheck.admin.domain.AppSettingRow;
import com.catcheck.admin.domain.port.AppSettingAdminPort;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L69 {@code GET /admin/settings} va L70 {@code PATCH /admin/settings/{key}} (p8 §8.4.12 muc (f)).
 *
 * <p>Ba dieu p8 chot tuong minh cho L70 va deu duoc kiem o day: {@code If-Match} <b>bat buoc</b>
 * (thieu ⇒ {@code 428}), khoa la ⇒ {@code 404 SETTING_KEY_UNKNOWN}, va gia tri {@code secret} bi
 * che — p4 §H3 noi che "trong UI <b>va trong audit</b>", nen ca {@code before}/{@code after} cua
 * {@code audit_log} cung phai che.</p>
 */
class AdminSettingsServiceTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-06T10:00:00Z");

    private final FakeAppSettingPort port = new FakeAppSettingPort();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final AdminSettingsService service = new AdminSettingsService(port, audit);

    @Test
    @DisplayName("L69 che gia tri cua khoa secret, giu ten/kieu/mo ta/moc sua")
    void listMasksSecretValuesOnly() {
        port.put(row("app.build_version", "1.2.3", "STRING", false));
        port.put(row("integration.webhook_signing_hint", "s3cr3t-hint", "STRING", true));

        Map<String, AppSettingRow> byKey = new LinkedHashMap<>();
        service.listMasked().forEach(row -> byKey.put(row.key(), row));

        assertThat(byKey.get("app.build_version").value()).isEqualTo("1.2.3");
        assertThat(byKey.get("integration.webhook_signing_hint").value())
                .as("p8 L69: gia tri secret = true bi che")
                .isEqualTo(AppSettingRow.MASK)
                .isNotEqualTo("s3cr3t-hint");
        // Metadata van phai hien: admin can thay "khoa nay ton tai va vua bi doi luc nao".
        assertThat(byKey.get("integration.webhook_signing_hint").valueType()).isEqualTo("STRING");
        assertThat(byKey.get("integration.webhook_signing_hint").updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    @DisplayName("L70 thieu If-Match ⇒ 428 PRECONDITION_REQUIRED (p8 §8.1.11)")
    void missingIfMatchYields428() {
        port.put(row("cat.max_per_user", "8", "INT", false));

        for (String missing : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> update("cat.max_per_user", "9", missing))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((CatCheckException) ex).errorCode().status().value())
                    .isEqualTo(428);
        }
        assertThat(port.updates).as("khong duoc ghi gi khi thieu If-Match").isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    @DisplayName("L70 If-Match khong khop ⇒ 412 RESOURCE_MODIFIED")
    void staleIfMatchYields412() {
        port.put(row("cat.max_per_user", "8", "INT", false));

        assertThatThrownBy(() -> update("cat.max_per_user", "9", "W/\"1-cat.max_per_user\""))
                .isInstanceOf(CatCheckException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode().status().value())
                .isEqualTo(412);
        assertThat(port.updates).isEmpty();
    }

    @Test
    @DisplayName("L70 khoa la ⇒ 404 SETTING_KEY_UNKNOWN (p8 §8.2.4(j)), KHONG tao khoa moi")
    void unknownKeyYields404AndCreatesNothing() {
        assertThatThrownBy(() -> update("khoa.khong.ton.tai", "x", "W/\"*\""))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode().code())
                .isEqualTo("SETTING_KEY_UNKNOWN");
        assertThat(port.updates).as("L70 khong duoc tao khoa moi — danh muc khoa thuoc p4 §H3")
                .isEmpty();
    }

    @Test
    @DisplayName("L70 duong thanh cong: ghi gia tri + ghi audit co reason, tra ETag moi")
    void happyPathWritesValueAndAudit() {
        port.put(row("cat.max_per_user", "8", "INT", false));
        String etag = AdminSettingsService.etagOf(port.findByKey("cat.max_per_user").orElseThrow());

        AppSettingRow updated = update("cat.max_per_user", "6", etag);

        assertThat(updated.value()).isEqualTo("6");
        assertThat(port.updates).containsExactly("cat.max_per_user=6");
        assertThat(audit.events).hasSize(1);
        AuditEvent event = audit.events.getFirst();
        assertThat(event.action()).isEqualTo("ADMIN_SETTING_UPDATED");
        assertThat(event.metadata()).containsEntry("key", "cat.max_per_user")
                .containsEntry("reason", "ha tran so ho so meo theo yeu cau van hanh");
        assertThat(event.before()).containsEntry("value", "8");
        assertThat(event.after()).containsEntry("value", "6");
    }

    @Test
    @DisplayName("L70 voi khoa secret: audit.before/after bi CHE (p4 §H3 'che trong UI va trong audit')")
    void secretKeyIsMaskedInAuditToo() {
        port.put(row("integration.webhook_signing_hint", "old-secret", "STRING", true));
        String etag = AdminSettingsService.etagOf(
                port.findByKey("integration.webhook_signing_hint").orElseThrow());

        AppSettingRow updated = update("integration.webhook_signing_hint", "new-secret", etag);

        assertThat(updated.value()).isEqualTo(AppSettingRow.MASK);
        AuditEvent event = audit.events.getFirst();
        assertThat(event.before()).containsEntry("value", AppSettingRow.MASK);
        assertThat(event.after()).containsEntry("value", AppSettingRow.MASK);
        assertThat(event.before().toString()).doesNotContain("old-secret");
        assertThat(event.after().toString()).doesNotContain("new-secret");
        assertThat(event.metadata()).containsEntry("secret", true);
    }

    private AppSettingRow update(String key, String value, String ifMatch) {
        return service.update(key, value, ifMatch, ADMIN, "ADMIN_SUPER",
                "ha tran so ho so meo theo yeu cau van hanh", "req-1", "127.0.0.1", "curl");
    }

    private static AppSettingRow row(String key, String value, String type, boolean secret) {
        return new AppSettingRow(key, value, type, "mo ta", secret, null, UPDATED_AT);
    }

    /** Khong dung Testcontainers: dieu can kiem la LUAT (che, If-Match, 404), khong phai SQL. */
    private static final class FakeAppSettingPort implements AppSettingAdminPort {

        private final Map<String, AppSettingRow> rows = new LinkedHashMap<>();
        private final List<String> updates = new ArrayList<>();

        void put(AppSettingRow row) {
            rows.put(row.key(), row);
        }

        @Override
        public List<AppSettingRow> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public Optional<AppSettingRow> findByKey(String key) {
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public Optional<AppSettingRow> updateValue(String key, String rawValue, UUID updatedBy) {
            AppSettingRow current = rows.get(key);
            if (current == null) {
                return Optional.empty();
            }
            updates.add(key + '=' + rawValue);
            AppSettingRow next = new AppSettingRow(key, rawValue, current.valueType(),
                    current.description(), current.secret(), updatedBy, current.updatedAt());
            rows.put(key, next);
            return Optional.of(next);
        }
    }

    private static final class RecordingAuditLog implements AuditLogService {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }
    }
}
