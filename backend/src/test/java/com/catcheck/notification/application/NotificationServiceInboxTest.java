package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.notification.application.NotificationTestDoubles.FakeConsentGate;
import com.catcheck.notification.application.NotificationTestDoubles.FakeEmailOutbox;
import com.catcheck.notification.application.NotificationTestDoubles.FakeNotifications;
import com.catcheck.notification.application.NotificationTestDoubles.FakePreferences;
import com.catcheck.notification.application.NotificationTestDoubles.FakePushOutbox;
import com.catcheck.notification.application.NotificationTestDoubles.FakeRecipients;
import com.catcheck.notification.application.NotificationTestDoubles.FakeSubscriptions;
import com.catcheck.notification.domain.port.NotificationRepository.InboxCursor;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceInboxTest {

    private static final UUID USER = UUID.randomUUID();

    private final FakeNotifications notifications = new FakeNotifications();

    private final NotificationService service = new NotificationService(notifications,
            new FakePushOutbox(), new FakeEmailOutbox(), new FakeSubscriptions(), new FakePreferences(),
            new FakeConsentGate(), new FakeRecipients(), new NotificationProperties(null, null),
            Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "%%%", "bm8tc2VwYXJhdG9y", "MTIzLm5vdC1hLXV1aWQ"})
    void malformedCursorIsRejectedWithPaginationCursorInvalid(String cursor) {
        assertThatThrownBy(() -> service.listInbox(USER, cursor, 20))
                .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
                        assertThat(ex.errorCode()).isEqualTo(NotificationErrorCode.PAGINATION_CURSOR_INVALID));
        assertThat(notifications.lastInboxCursor).isNull();
    }

    @Test
    void wellFormedCursorStillReadsThePage() {
        Instant createdAt = Instant.parse("2026-10-10T07:15:30.123Z");
        UUID id = UUID.randomUUID();
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (createdAt.toEpochMilli() + "." + id).getBytes(StandardCharsets.UTF_8));

        assertThat(service.listInbox(USER, cursor, 20).items()).isEmpty();
        assertThat(notifications.lastInboxCursor).isEqualTo(new InboxCursor(createdAt, id));
    }

    @Test
    void cursorIssuedAsNextCursorRoundTrips() {
        InboxCursor issued = new InboxCursor(Instant.parse("2026-10-10T07:15:30.123Z"), UUID.randomUUID());

        service.listInbox(USER, issued.encode(), 20);

        assertThat(notifications.lastInboxCursor).isEqualTo(issued);
    }

    @Test
    void missingCursorReadsTheFirstPage() {
        assertThat(service.listInbox(USER, null, 20).hasMore()).isFalse();
        assertThat(service.listInbox(USER, " ", 20).hasMore()).isFalse();
        assertThat(notifications.lastInboxCursor).isNull();
    }
}
