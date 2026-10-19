package com.catcheck.credit.infrastructure.notification;

import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.shared.i18n.MessageResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cùng một mốc hết hạn, tiêu đề/nội dung theo {@code app_user.locale} của người nhận. */
class CreditExpiryNotificationAdapterTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-20T00:00:00Z");

    private final NotificationGateway gateway = mock(NotificationGateway.class);
    private final CreditExpiryNotificationAdapter adapter =
            new CreditExpiryNotificationAdapter(gateway, messages());

    @Test
    void t48hFollowsTheRecipientLocale() {
        NotificationRequest vi = queue("vi", ExpiryReminderMilestone.T48H);
        NotificationRequest en = queue("en", ExpiryReminderMilestone.T48H);

        assertThat(vi.title()).isEqualTo("Credit sắp hết hạn");
        assertThat(vi.body()).isEqualTo("Bạn còn 5 credit sắp hết hạn trong 2 ngày. Dùng ngay để không bị mất.");
        assertThat(en.title()).isEqualTo("Credit expiring soon");
        assertThat(en.body()).isEqualTo("You have 5 credit expiring within 2 days. Use it now so it is not lost.");
    }

    @Test
    void t6hFollowsTheRecipientLocale() {
        NotificationRequest vi = queue("vi", ExpiryReminderMilestone.T6H);
        NotificationRequest en = queue("en", ExpiryReminderMilestone.T6H);

        assertThat(vi.title()).isEqualTo("Credit sắp hết hạn — còn 6 giờ");
        assertThat(vi.body()).isEqualTo("Chỉ còn 6 giờ trước khi 5 credit hết hạn.");
        assertThat(en.title()).isEqualTo("Credit expiring soon — 6 hours left");
        assertThat(en.body()).isEqualTo("Only 6 hours left before 5 credit expires.");
    }

    private NotificationRequest queue(String locale, ExpiryReminderMilestone milestone) {
        when(gateway.recipientLocale(USER)).thenReturn(Locale.forLanguageTag(locale));
        adapter.queueExpiryReminder(USER, UUID.randomUUID(), milestone, EXPIRES_AT, 5);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(gateway, atLeastOnce()).enqueue(captor.capture());
        return captor.getValue();
    }

    private static MessageResolver messages() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames("classpath:messages/notification");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        return new MessageResolver(source);
    }
}
