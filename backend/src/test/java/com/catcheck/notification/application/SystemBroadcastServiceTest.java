package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.NotificationRecipient;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L72 fan-out — {@code SystemBroadcastService}.
 *
 * <p><b>Day la duong kiem chung thay cho viec bam that.</b> L72 ghi mot ban ghi cho MOI user; tren
 * DB dung chung giua nhieu agent, bam that de "xem co chay khong" la khong hoan tac duoc. Test
 * nay dung mot {@code NotificationGateway} gia nen kiem duoc <b>dung nhung dieu can kiem</b> —
 * so nguoi nhan, keyset phan trang, {@code dedupeKey}, va dry-run khong ghi gi — ma khong gui mot
 * thong bao nao.</p>
 */
class SystemBroadcastServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:34:56Z");

    private final CountingGateway gateway = new CountingGateway();
    private final FakeRecipients recipients = new FakeRecipients();
    private final SystemBroadcastService service = new SystemBroadcastService(
            gateway, recipients, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("dryRun = true DEM nguoi nhan nhung KHONG ghi ban ghi nao")
    void dryRunCountsWithoutWriting() {
        recipients.seed(1_200);

        SystemBroadcastGateway.BroadcastResult result = service.broadcast(
                "SYSTEM_MAINTENANCE", "Bao tri", "Tu 01:00 den 02:00", Map.of(), true);

        assertThat(result.recipientsMatched()).isEqualTo(1_200);
        assertThat(result.notificationsQueued()).isZero();
        assertThat(gateway.enqueued).as("dry-run tuyet doi khong goi enqueue").isEmpty();
    }

    @Test
    @DisplayName("Phat that di qua keyset nhieu lo va ghi dung mot ban ghi moi nguoi nhan")
    void realBroadcastPagesThroughEveryRecipientExactlyOnce() {
        recipients.seed(1_200);

        SystemBroadcastGateway.BroadcastResult result = service.broadcast(
                "SYSTEM_MAINTENANCE", "Bao tri", "Tu 01:00 den 02:00", Map.of(), false);

        assertThat(result.recipientsMatched()).isEqualTo(1_200);
        assertThat(result.notificationsQueued()).isEqualTo(1_200);
        assertThat(gateway.enqueued).hasSize(1_200);
        assertThat(gateway.enqueued.stream().map(NotificationRequest::userId).distinct().count())
                .as("khong ai bi gui hai lan trong cung mot lan phat")
                .isEqualTo(1_200);
        // 1200 / 500 = 3 lo (500 + 500 + 200): lo cuoi ngan hon page size nen vong lap dung.
        assertThat(recipients.calls).isEqualTo(3);
    }

    @Test
    @DisplayName("dedupeKey theo quy uoc p12 §12.4 — cung tien to cho ca lan phat, khac nhau theo user")
    void dedupeKeysSharePrefixAndDifferPerUser() {
        recipients.seed(3);

        SystemBroadcastGateway.BroadcastResult result = service.broadcast(
                "PRIVACY_INCIDENT_NOTICE", "Thong bao su co", "Noi dung du 6 muc", Map.of(), false);

        assertThat(result.dedupeKeyPrefix()).startsWith("PRIVACY_INCIDENT_NOTICE:BROADCAST:");
        List<String> keys = gateway.enqueued.stream().map(NotificationRequest::dedupeKey).toList();
        assertThat(keys).allSatisfy(key -> assertThat(key).startsWith(result.dedupeKeyPrefix() + ':'));
        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("PRIVACY_INCIDENT_NOTICE co trong registry template, bat buoc gui, khong quiet hours")
    void privacyIncidentNoticeIsRegisteredAsMandatoryWithoutQuietHours() {
        NotificationTemplate template = NotificationTemplate.fromCode("PRIVACY_INCIDENT_NOTICE")
                .orElseThrow(() -> new AssertionError(
                        "p8 L72 doi template PRIVACY_INCIDENT_NOTICE; p12 §12.2.5 dinh nghia no"));

        // p12 §12.2.5: nghia vu luat dinh ⇒ gui ke ca khi user tat moi thong bao.
        assertThat(template.mandatory()).isTrue();
        // Dieu 29.1.a ND356 cho dung 72 gio: hoan den 07:00 co the lam vo chinh han do.
        assertThat(template.quietHoursApply()).isFalse();
        // Can cu la NV (nghia vu phap luat), KHONG phai consent.
        assertThat(template.emailConsentPurpose()).isEmpty();
        assertThat(template.marketing()).isFalse();
        assertThat(template.supportsEmail()).isTrue();
        assertThat(template.writesInAppRecord()).isTrue();
        // p12 §12.2.5 ghi kenh la "Email + In-app + banner cong khai" — khong co push: noi dung
        // phai du 6 muc Dieu 29.2, khong nhet duoc vao mot banner.
        assertThat(template.supportsPush()).isFalse();
    }

    @Test
    @DisplayName("Template ngoai hai gia tri p8 L72 bi tu choi ngay o cong, khong doi controller chan")
    void gatewayRejectsDisallowedTemplateItself() {
        recipients.seed(1);

        assertThatThrownBy(() -> service.broadcast(
                "REMINDER_SCAN_DUE", "x", "y", Map.of(), false))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(gateway.enqueued).isEmpty();
    }

    /** Dem loi goi {@code enqueue}; KHONG gui gi. */
    private static final class CountingGateway implements NotificationGateway {

        private final List<NotificationRequest> enqueued = new ArrayList<>();

        @Override
        public void enqueue(NotificationRequest request) {
            enqueued.add(request);
        }

        @Override
        public void enqueueTransactionalEmail(TransactionalEmailRequest request) {
            throw new AssertionError("L72 khong gui email giao dich truc tiep");
        }

        @Override
        public int revokePushOnConsentWithdrawal(UUID userId) {
            throw new AssertionError("khong lien quan");
        }
    }

    /** Sinh id tang dan de keyset hoat dong giong UUID v7 that. */
    private static final class FakeRecipients implements NotificationRecipientPort {

        private final List<UUID> ids = new ArrayList<>();
        private int calls;

        void seed(int count) {
            for (int i = 1; i <= count; i++) {
                ids.add(UUID.fromString("00000000-0000-7000-8000-%012d".formatted(i)));
            }
        }

        @Override
        public Optional<NotificationRecipient> findById(UUID userId) {
            return Optional.empty();
        }

        @Override
        public List<UUID> findActiveRecipientIdsAfter(UUID afterId, int limit) {
            calls++;
            return ids.stream()
                    .filter(id -> afterId == null || id.compareTo(afterId) > 0)
                    .limit(limit)
                    .toList();
        }
    }
}
