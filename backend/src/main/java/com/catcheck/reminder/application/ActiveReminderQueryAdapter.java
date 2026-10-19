package com.catcheck.reminder.application;

import com.catcheck.reminder.api.ActiveReminderQuery;
import com.catcheck.reminder.domain.port.ReminderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ActiveReminderQueryAdapter implements ActiveReminderQuery {

    private final ReminderRepository repository;

    public ActiveReminderQueryAdapter(ReminderRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ActiveReminder> activeRemindersFor(UUID userId, UUID catId) {
        return repository.findAllForUser(userId, catId, true).stream()
                .map(reminder -> new ActiveReminder(reminder.type().name(), reminder.nextRunAt()))
                .toList();
    }
}
