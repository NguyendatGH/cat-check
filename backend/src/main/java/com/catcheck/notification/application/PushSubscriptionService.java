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

    @Transactional(readOnly = true)
    public List<PushSubscription> list(UUID userId) {
        return repository.findActiveByUser(userId);
    }

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
            repository.revokeInstallationIdOwnedByOtherUser(installationId, command.userId(), now);
        }
        if (legacyToken != null) {
            repository.revokeLegacyTokenOwnedByOtherUser(legacyToken, command.userId(), now);
        }
        return repository.upsert(new PushSubscription(
                null, command.userId(), installationId, legacyToken,
                platform(command.platform()), command.deviceLabel(), command.userAgent(),
                now, null, null, 0, null, null, now));
    }

    @Transactional
    public void revoke(UUID userId, UUID subscriptionId) {
        repository.findByIdForUser(subscriptionId, userId)
                .orElseThrow(() -> new NotFoundException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        repository.revoke(subscriptionId, userId, PushRevokeReason.USER_DISABLED, clock.instant());
    }

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
