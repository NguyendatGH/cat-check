package com.catcheck.notification.application;

import com.catcheck.notification.application.NotificationTestDoubles.FakeConsentGate;
import com.catcheck.notification.application.NotificationTestDoubles.FakeSubscriptions;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.notification.domain.PushPlatform;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** G10 — thiết bị đổi chủ phải thu hồi đăng ký của user trước, kể cả khi chỉ có legacy_token. */
class PushSubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final UUID PREVIOUS_OWNER = UUID.randomUUID();
    private static final UUID NEW_OWNER = UUID.randomUUID();

    private final FakeSubscriptions subscriptions = new FakeSubscriptions();
    private final FakeConsentGate consentGate = new FakeConsentGate();
    private final PushSubscriptionService service = new PushSubscriptionService(
            subscriptions, consentGate, new NotificationProperties(null, null), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        consentGate.granted.add(NotificationTemplate.ConsentPurposes.HEALTH_REMINDER_PUSH);
    }

    @Test
    void legacyTokenHandOffRevokesPreviousOwner() {
        PushSubscription previous = device(PREVIOUS_OWNER, null, "legacy-token-1");
        subscriptions.active.add(previous);

        service.upsert(new PushSubscriptionCommands.Upsert(NEW_OWNER, null, "legacy-token-1", "WEB", null, null));

        assertThat(subscriptions.revoked).containsEntry(previous.id(), PushRevokeReason.USER_DISABLED);
        assertThat(subscriptions.findActiveByUser(PREVIOUS_OWNER)).isEmpty();
    }

    @Test
    void installationIdHandOffStillRevokesPreviousOwner() {
        PushSubscription previous = device(PREVIOUS_OWNER, "fid-1", null);
        subscriptions.active.add(previous);

        service.upsert(new PushSubscriptionCommands.Upsert(NEW_OWNER, "fid-1", null, "WEB", null, null));

        assertThat(subscriptions.revoked).containsKey(previous.id());
    }

    @Test
    void sameOwnerReRegisteringKeepsOwnDevice() {
        PushSubscription own = device(NEW_OWNER, null, "legacy-token-1");
        subscriptions.active.add(own);

        service.upsert(new PushSubscriptionCommands.Upsert(NEW_OWNER, null, "legacy-token-1", "WEB", null, null));

        assertThat(subscriptions.revoked).doesNotContainKey(own.id());
    }

    private static PushSubscription device(UUID owner, String fid, String legacyToken) {
        return new PushSubscription(UUID.randomUUID(), owner, fid, legacyToken, PushPlatform.WEB,
                null, null, NOW, null, null, 0, null, null, NOW);
    }
}
