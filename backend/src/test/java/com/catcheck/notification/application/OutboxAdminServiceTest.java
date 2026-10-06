package com.catcheck.notification.application;

import com.catcheck.notification.api.OutboxAdminGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L67 — {@code OutboxAdminService}.
 *
 * <p>Diem can kiem: service <b>phan biet</b> ba ket qua (da dua lai hang cho / khong tim thay /
 * khong phai {@code FAILED}) va tim o <b>ca hai</b> bang outbox. L66 tra ve mot danh sach da
 * {@code UNION ALL} hai bang nen nguoi bam nut khong biet dong do thuoc bang nao — neu service chi
 * tim mot bang thi mot nua cac dong {@code FAILED} se tra {@code 404} oan.</p>
 */
class OutboxAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private final NotificationTestDoubles.FakeEmailOutbox emailOutbox =
            new NotificationTestDoubles.FakeEmailOutbox();
    private final NotificationTestDoubles.FakePushOutbox pushOutbox =
            new NotificationTestDoubles.FakePushOutbox();
    private final OutboxAdminService service = new OutboxAdminService(
            emailOutbox, pushOutbox, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("Dong FAILED o email_outbox duoc dua lai hang cho voi next_attempt_at = now")
    void requeuesFailedEmailRow() {
        UUID id = UUID.randomUUID();
        emailOutbox.markFailed(id, 5, "SMTP timeout");

        assertThat(service.resendFailed(id)).isEqualTo(OutboxAdminGateway.ResendOutcome.REQUEUED);
        assertThat(emailOutbox.requeued).containsEntry(id, NOW);
    }

    @Test
    @DisplayName("Dong FAILED o notification_outbox cung duoc tim — L66 hop nhat hai bang")
    void requeuesFailedPushRowToo() {
        UUID id = UUID.randomUUID();
        pushOutbox.markFailed(id, 5, "UNREGISTERED", "token het hieu luc");

        assertThat(service.resendFailed(id)).isEqualTo(OutboxAdminGateway.ResendOutcome.REQUEUED);
        assertThat(pushOutbox.requeued).containsEntry(id, NOW);
    }

    @Test
    @DisplayName("Id khong co o bang nao ⇒ NOT_FOUND (⇒ 404)")
    void unknownIdIsNotFound() {
        assertThat(service.resendFailed(UUID.randomUUID()))
                .isEqualTo(OutboxAdminGateway.ResendOutcome.NOT_FOUND);
    }

    @Test
    @DisplayName("Dong ton tai nhung khong FAILED ⇒ NOT_FAILED (⇒ 409), khong phai 404")
    void existingButNotFailedRowIsAConflict() {
        UUID id = UUID.randomUUID();
        pushOutbox.markSent(id, "fcm-1", NOW);

        assertThat(service.resendFailed(id))
                .as("p8 L67 chi cho gui lai ban ghi FAILED; mot dong SENT gui lai = gui lan hai")
                .isEqualTo(OutboxAdminGateway.ResendOutcome.NOT_FAILED);
    }

    @Test
    @DisplayName("Bam hai lan: lan hai khong con FAILED ⇒ NOT_FAILED, khong dua lai hang cho lan nua")
    void secondClickDoesNotRequeueAgain() {
        UUID id = UUID.randomUUID();
        emailOutbox.markFailed(id, 5, "SMTP timeout");

        assertThat(service.resendFailed(id)).isEqualTo(OutboxAdminGateway.ResendOutcome.REQUEUED);
        assertThat(service.resendFailed(id)).isEqualTo(OutboxAdminGateway.ResendOutcome.NOT_FAILED);
        assertThat(emailOutbox.requeued).hasSize(1);
    }
}
