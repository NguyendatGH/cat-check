package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.notification.application.spi.ConsentGatePort;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.notification.domain.PushPlatform;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;
import com.catcheck.notification.domain.port.PushSubscriptionRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Đăng ký thiết bị nhận web push — p8 §8.4.7 G9–G11.
 *
 * <p>Ba bất biến của p4 F3 được ép ở đây và ở DB, không chỉ một trong hai:</p>
 * <ul>
 *   <li>Thiếu cả {@code fid} lẫn {@code legacyToken} ⇒ {@code PUSH_SUBSCRIPTION_INVALID}
 *       (CHECK {@code ck_push_identifier} là lớp thứ hai).</li>
 *   <li>Trần <b>10 thiết bị/user</b> ⇒ {@code PUSH_SUBSCRIPTION_LIMIT} (khớp trần 10 phiên đồng
 *       thời của p11 §11.1.3).</li>
 *   <li>Một FID chỉ thuộc MỘT user đang hoạt động: hai người dùng chung máy thì đăng ký cho
 *       người mới phải thu hồi đăng ký của người cũ, nếu không người sau nhận push của người
 *       trước (partial unique {@code uq_push_subscription_fid_active}).</li>
 * </ul>
 */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final ConsentGatePort consentGate;
    private final NotificationProperties properties;
    private final Clock clock;

    public PushSubscriptionService(PushSubscriptionRepository repository,
                                   ConsentGatePort consentGate,
                                   NotificationProperties properties,
                                   Clock clock) {
        this.repository = repository;
        this.consentGate = consentGate;
        this.properties = properties;
        this.clock = clock;
    }

    /** G9 — {@code GET /push/subscriptions}. Không phân trang: tập có trần cứng (p8 §8.1.4). */
    @Transactional(readOnly = true)
    public List<PushSubscription> list(UUID userId) {
        return repository.findActiveByUser(userId);
    }

    /** G10 — {@code PUT /push/subscriptions}. Auth của p8: {@code U + C:HEALTH_REMINDER_PUSH}. */
    @Transactional
    public PushSubscription upsert(PushSubscriptionCommands.Upsert command) {
        String installationId = blankToNull(command.installationId());
        String legacyToken = blankToNull(command.legacyToken());
        if (installationId == null && legacyToken == null) {
            throw new BusinessRuleException(NotificationErrorCode.PUSH_SUBSCRIPTION_INVALID);
        }
        if (!consentGate.isGranted(command.userId(), NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH)) {
            throw new PermissionDeniedException(NotificationErrorCode.CONSENT_REQUIRED,
                    NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);
        }

        Instant now = clock.instant();
        String deviceKey = installationId != null ? installationId : legacyToken;
        boolean existing = repository.findActiveByDeviceKey(command.userId(), deviceKey).isPresent();
        int max = properties.push().maxDevicesPerUser();
        if (!existing && repository.countActiveByUser(command.userId()) >= max) {
            throw new ConflictException(NotificationErrorCode.PUSH_SUBSCRIPTION_LIMIT, max);
        }
        if (installationId != null) {
            // Thiết bị đổi chủ: thu hồi đăng ký của user cũ TRƯỚC khi upsert, nếu không partial
            // unique uq_push_subscription_fid_active sẽ chặn INSERT.
            repository.revokeInstallationIdOwnedByOtherUser(installationId, command.userId(), now);
        }
        return repository.upsert(new PushSubscription(
                null, command.userId(), installationId, legacyToken,
                platform(command.platform()), command.deviceLabel(), command.userAgent(),
                now, null, null, 0, null, null, now));
    }

    /** G11 — {@code DELETE /push/subscriptions/{id}}, {@code revoke_reason = USER_DISABLED}. */
    @Transactional
    public void revoke(UUID userId, UUID subscriptionId) {
        repository.findByIdForUser(subscriptionId, userId)
                .orElseThrow(() -> new NotFoundException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        repository.revoke(subscriptionId, userId, PushRevokeReason.USER_DISABLED, clock.instant());
    }

    /**
     * Rút consent {@code HEALTH_REMINDER_PUSH} ⇒ thu hồi NGAY, không chờ job dọn định kỳ
     * (p12 §12.3.9, p15 §15.3.5).
     *
     * <p><b>Chưa có ai gọi.</b> Người gọi đúng là module {@code privacy} lúc ghi
     * {@code consent_record(WITHDRAWN)} — nhưng {@code privacy} nằm ngoài phạm vi gói việc này
     * và phải gọi qua named interface {@code notification::api}, nên việc nối dây còn lại là
     * một đợt sau. Năng lực thì đã có ở đây để đợt đó chỉ cần thêm một phương thức vào
     * {@code NotificationGateway}.</p>
     */
    @Transactional
    public int revokeAllForUser(UUID userId, PushRevokeReason reason) {
        Instant now = clock.instant();
        List<PushSubscription> active = repository.findActiveByUser(userId);
        active.forEach(subscription -> repository.revokeById(subscription.id(), reason, now));
        return active.size();
    }

    private PushPlatform platform(String raw) {
        if (raw == null || raw.isBlank()) {
            return PushPlatform.WEB;
        }
        for (PushPlatform candidate : PushPlatform.values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw new BusinessRuleException(NotificationErrorCode.VALIDATION_FAILED, "platform");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
