package com.catcheck.reminder.application;

import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.reminder.application.spi.CatOwnershipPort;
import com.catcheck.reminder.domain.NextRunCalculator;
import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.port.ReminderRepository;
import com.catcheck.shared.i18n.MessageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DueReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DueReminderDispatcher.class);

    static final Duration OVERDUE_AFTER = Duration.ofHours(24);

    static final String TEMPLATE_SCAN_DUE = "REMINDER_SCAN_DUE";

    static final String TEMPLATE_OVERDUE = "REMINDER_OVERDUE";

    static final String KEY_SCAN_DUE = "notif.reminder.scan_due";

    static final String KEY_OVERDUE = "notif.reminder.overdue";

    private static final String REF_TYPE_REMINDER = "REMINDER";

    private final ReminderRepository repository;
    private final CatOwnershipPort cats;
    private final NotificationGateway notifications;
    private final MessageResolver messages;

    public DueReminderDispatcher(ReminderRepository repository,
                                 CatOwnershipPort cats,
                                 NotificationGateway notifications,
                                 MessageResolver messages) {
        this.repository = repository;
        this.cats = cats;
        this.notifications = notifications;
        this.messages = messages;
    }

    /**
     * RRULE reminders created before RRULE scheduling existed were stored with
     * {@code next_run_at = NULL} and are never picked up by {@link #findDue}. Unsupported rules
     * are skipped (they stay NULL until the user edits them).
     *
     * @return number of reminders that got a {@code next_run_at}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillRruleNextRuns(Instant now, int limit) {
        int scheduled = 0;
        UUID cursor = null;
        while (true) {
            List<Reminder> page = repository.findUnscheduledRrule(cursor, limit);
            for (Reminder reminder : page) {
                Instant next;
                try {
                    next = NextRunCalculator.nextRun(reminder, now);
                } catch (RuntimeException unsupported) {
                    log.debug("Bo qua reminder {}: rrule ngoai tap ho tro.", reminder.id());
                    continue;
                }
                if (next != null && repository.scheduleIfUnscheduled(reminder.id(), next)) {
                    scheduled++;
                }
            }
            if (page.size() < limit) {
                return scheduled;
            }
            cursor = page.getLast().id();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<UUID> findDue(Instant now, int limit) {
        return repository.findDueIds(now, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean dispatch(UUID reminderId, Instant now) {
        Optional<Reminder> locked = repository.lockDue(reminderId, now);
        if (locked.isEmpty()) {
            return false;
        }
        Reminder reminder = locked.get();

        Optional<String> catName = cats.findAliveCatName(reminder.catId(), reminder.userId());
        if (catName.isEmpty()) {
            Instant next = NextRunCalculator.nextRun(reminder, now);
            repository.markDispatched(reminder.id(), reminder.lastRunAt(), next);
            log.warn("Bo qua reminder {}: ho so meo khong con (da doi next_run_at).", reminder.id());
            return false;
        }

        boolean overdue = isOverdue(reminder, now);
        String template = overdue ? TEMPLATE_OVERDUE : TEMPLATE_SCAN_DUE;
        String messageKey = overdue ? KEY_OVERDUE : KEY_SCAN_DUE;
        Locale locale = notifications.recipientLocale(reminder.userId());
        notifications.enqueue(new NotificationRequest(
                reminder.userId(),
                template,
                payload(reminder, catName.get()),
                messages.resolve(messageKey + ".title", locale),
                messages.resolve(messageKey + ".body", new Object[]{catName.get()}, locale),
                REF_TYPE_REMINDER,
                reminder.id(),
                dedupeKey(template, reminder)));

        repository.markDispatched(reminder.id(), now, NextRunCalculator.nextRun(reminder, now));
        log.debug("Da xep hang {} cho reminder {}", template, reminder.id());
        return true;
    }

    private static boolean isOverdue(Reminder reminder, Instant now) {
        Instant due = reminder.nextRunAt();
        if (due == null || now.isBefore(due.plus(OVERDUE_AFTER))) {
            return false;
        }
        Instant satisfied = reminder.lastSatisfiedAt();
        return satisfied == null || satisfied.isBefore(due);
    }

    private static Map<String, Object> payload(Reminder reminder, String catName) {
        return Map.of(
                "catId", reminder.catId().toString(),
                "catName", catName,
                "reminderId", reminder.id().toString(),
                "deepLink", "/scan?catId=" + reminder.catId());
    }

    private static String dedupeKey(String template, Reminder reminder) {
        return template + ":REMINDER:" + reminder.id() + ':' + reminder.nextRunAt().getEpochSecond();
    }
}
