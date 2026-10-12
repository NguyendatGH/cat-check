package com.catcheck.reminder.application;

import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.reminder.application.spi.CatOwnershipPort;
import com.catcheck.reminder.domain.NextRunCalculator;
import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.port.ReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DueReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DueReminderDispatcher.class);

    static final Duration OVERDUE_AFTER = Duration.ofHours(24);

    static final String TEMPLATE_SCAN_DUE = "REMINDER_SCAN_DUE";

    static final String TEMPLATE_OVERDUE = "REMINDER_OVERDUE";

    private static final String REF_TYPE_REMINDER = "REMINDER";

    private final ReminderRepository repository;
    private final CatOwnershipPort cats;
    private final NotificationGateway notifications;

    public DueReminderDispatcher(ReminderRepository repository,
                                 CatOwnershipPort cats,
                                 NotificationGateway notifications) {
        this.repository = repository;
        this.cats = cats;
        this.notifications = notifications;
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
        notifications.enqueue(new NotificationRequest(
                reminder.userId(),
                template,
                payload(reminder, catName.get()),
                title(overdue),
                body(overdue, catName.get()),
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

    private static String title(boolean overdue) {
        return overdue ? "Đã quá hạn theo dõi" : "Đến lịch quét định kỳ";
    }

    private static String body(boolean overdue, String catName) {
        return overdue
                ? catName + " chưa được quét theo lịch. Quét ngay để không bỏ lỡ thay đổi."
                : "Đến lịch quét cho " + catName + " hôm nay.";
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
