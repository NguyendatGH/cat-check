package com.catcheck.notification.application;

import com.catcheck.notification.api.OutboxAdminGateway;
import com.catcheck.notification.domain.port.EmailOutboxRepository;
import com.catcheck.notification.domain.port.NotificationOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * L67 {@code POST /api/v1/admin/notifications/outbox/{id}/resend} — cai dat
 * {@link OutboxAdminGateway}.
 *
 * <p><b>Thu tu thu la email truoc, push sau</b>, va khong co cach nao biet truoc dong thuoc bang
 * nao: L66 tra ve mot danh sach da {@code UNION ALL} hai bang, nen id ma admin bam den tu mot
 * trong hai. Id la UUID v7 nen khong the trung giua hai bang; neu mot ngay nao do trung, lan
 * {@code requeueFailed} dau tien thanh cong se thang va day la hanh vi dung (mot dong duoc dua
 * lai hang cho, khong phai hai).</p>
 *
 * <p><b>{@code requeueFailed} tra {@code false} khong co nghia la "khong tim thay".</b> Co hai ly
 * do khac nhau va chung phai thanh hai ma HTTP khac nhau (404 vs 409), nen phai hoi them
 * {@code findStatus}. Goi mot cau {@code SELECT} nua la cai gia de nguoi van hanh phan biet
 * "dong nay khong ton tai" voi "dong nay dang cho gui, dung bam nua".</p>
 */
@Service
public class OutboxAdminService implements OutboxAdminGateway {

    private final EmailOutboxRepository emailOutbox;
    private final NotificationOutboxRepository pushOutbox;
    private final Clock clock;

    public OutboxAdminService(EmailOutboxRepository emailOutbox,
                              NotificationOutboxRepository pushOutbox,
                              Clock clock) {
        this.emailOutbox = emailOutbox;
        this.pushOutbox = pushOutbox;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResendOutcome resendFailed(UUID outboxId) {
        Instant now = clock.instant();
        if (emailOutbox.requeueFailed(outboxId, now) || pushOutbox.requeueFailed(outboxId, now)) {
            return ResendOutcome.REQUEUED;
        }
        Optional<String> status = emailOutbox.findStatus(outboxId)
                .or(() -> pushOutbox.findStatus(outboxId));
        return status.isPresent() ? ResendOutcome.NOT_FAILED : ResendOutcome.NOT_FOUND;
    }
}
