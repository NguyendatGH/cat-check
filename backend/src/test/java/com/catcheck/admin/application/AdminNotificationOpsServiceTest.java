package com.catcheck.admin.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.notification.api.OutboxAdminGateway;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L67 {@code POST /admin/notifications/outbox/{id}/resend} va L72
 * {@code POST /admin/system/broadcast} (p8 §8.4.12 muc (f)).
 */
class AdminNotificationOpsServiceTest {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID OUTBOX_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");

    private final FakeOutboxAdmin outbox = new FakeOutboxAdmin();
    private final FakeBroadcast broadcast = new FakeBroadcast();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final AdminNotificationOpsService service =
            new AdminNotificationOpsService(outbox, broadcast, audit);

    // ----------------------------------------------------------------- L67

    @Test
    @DisplayName("L67 duong thanh cong: ghi audit, KHONG doi reason (p8 L67)")
    void resendRequeuesAndAuditsWithoutReason() {
        outbox.outcome = OutboxAdminGateway.ResendOutcome.REQUEUED;

        service.resendOutboxEntry(OUTBOX_ID, ADMIN, "ADMIN_SUPER", "req-1", "127.0.0.1", "curl");

        assertThat(outbox.requested).containsExactly(OUTBOX_ID);
        assertThat(audit.events).hasSize(1);
        AuditEvent event = audit.events.getFirst();
        assertThat(event.action()).isEqualTo("ADMIN_OUTBOX_RESEND");
        // Kieu UUID, KHONG phai String: `PiiRedactor` cua module audit bo qua gia tri kieu UUID
        // nhung che moi String dai >= 32 ky tu thanh "[REDACTED]_BLOB" — mot UUID da .toString()
        // se bien mat khoi so audit (do that tren DB local truoc khi sua).
        assertThat(event.metadata()).containsEntry("outboxId", OUTBOX_ID);
        assertThat(event.metadata().get("outboxId")).isInstanceOf(java.util.UUID.class);
        // p16 §16.6.3: dia chi nhan la PII, khong duoc vao audit metadata.
        assertThat(event.metadata().toString()).doesNotContain("@");
    }

    @Test
    @DisplayName("L67 khong tim thay ⇒ 404; khong phai FAILED ⇒ 409 — hai trang thai khac nhau")
    void resendDistinguishesNotFoundFromNotFailed() {
        outbox.outcome = OutboxAdminGateway.ResendOutcome.NOT_FOUND;
        assertThatThrownBy(this::resend)
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode().code())
                .isEqualTo("OUTBOX_ENTRY_NOT_FOUND");

        outbox.outcome = OutboxAdminGateway.ResendOutcome.NOT_FAILED;
        assertThatThrownBy(this::resend)
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode().status().value())
                .isEqualTo(409);

        assertThat(audit.events).as("hai nhanh loi khong ghi audit thanh cong").isEmpty();
    }

    // ----------------------------------------------------------------- L72

    @Test
    @DisplayName("L72 template ngoai hai gia tri p8 ⇒ 422, va KHONG goi cong gui")
    void broadcastRejectsDisallowedTemplate() {
        assertThatThrownBy(() -> broadcast("REMINDER_SCAN_DUE", false))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode().code())
                .isEqualTo("BROADCAST_TEMPLATE_NOT_ALLOWED");

        assertThat(broadcast.calls).as("khong duoc phat gi khi template khong hop le").isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    @DisplayName("L72 chap nhan dung hai template cua p8 L72")
    void broadcastAcceptsExactlyTheTwoAllowedTemplates() {
        assertThat(SystemBroadcastGateway.ALLOWED_TEMPLATE_CODES)
                .containsExactlyInAnyOrder("SYSTEM_MAINTENANCE", "PRIVACY_INCIDENT_NOTICE");

        broadcast("SYSTEM_MAINTENANCE", true);
        broadcast("PRIVACY_INCIDENT_NOTICE", true);

        assertThat(broadcast.calls).containsExactly(
                "SYSTEM_MAINTENANCE dryRun=true", "PRIVACY_INCIDENT_NOTICE dryRun=true");
    }

    @Test
    @DisplayName("L72 dryRun truyen nguyen xuong cong va duoc ghi vao audit")
    void broadcastPropagatesDryRunAndAuditsIt() {
        broadcast.result = new SystemBroadcastGateway.BroadcastResult(1234, 0, "PFX");

        SystemBroadcastGateway.BroadcastResult result = broadcast("SYSTEM_MAINTENANCE", true);

        assertThat(result.recipientsMatched()).isEqualTo(1234);
        assertThat(result.notificationsQueued()).as("dry-run khong ghi ban ghi nao").isZero();
        AuditEvent event = audit.events.getFirst();
        assertThat(event.action()).isEqualTo("ADMIN_SYSTEM_BROADCAST");
        assertThat(event.metadata())
                .containsEntry("dryRun", true)
                .containsEntry("recipientsMatched", 1234)
                .containsEntry("notificationsQueued", 0)
                .containsEntry("templateCode", "SYSTEM_MAINTENANCE")
                .containsEntry("reason", "thong bao bao tri dinh ky theo ke hoach");
    }

    private void resend() {
        service.resendOutboxEntry(OUTBOX_ID, ADMIN, "ADMIN_SUPER", "req-1", "127.0.0.1", "curl");
    }

    private SystemBroadcastGateway.BroadcastResult broadcast(String templateCode, boolean dryRun) {
        return service.broadcast(templateCode, "Tieu de", "Noi dung", Map.of(), dryRun,
                ADMIN, "ADMIN_SUPER", "thong bao bao tri dinh ky theo ke hoach",
                "req-1", "127.0.0.1", "curl");
    }

    private static final class FakeOutboxAdmin implements OutboxAdminGateway {

        private final List<UUID> requested = new ArrayList<>();
        private ResendOutcome outcome = ResendOutcome.REQUEUED;

        @Override
        public ResendOutcome resendFailed(UUID outboxId) {
            requested.add(outboxId);
            return outcome;
        }
    }

    private static final class FakeBroadcast implements SystemBroadcastGateway {

        private final List<String> calls = new ArrayList<>();
        private BroadcastResult result = new BroadcastResult(0, 0, "PFX");

        @Override
        public BroadcastResult broadcast(String templateCode, String title, String body,
                                        Map<String, Object> payload, boolean dryRun) {
            calls.add(templateCode + " dryRun=" + dryRun);
            return result;
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
