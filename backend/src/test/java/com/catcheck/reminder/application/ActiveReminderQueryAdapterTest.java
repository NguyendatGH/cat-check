package com.catcheck.reminder.application;

import com.catcheck.reminder.api.ActiveReminderQuery;
import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.ReminderSource;
import com.catcheck.reminder.domain.ReminderType;
import com.catcheck.reminder.domain.ScheduleKind;
import com.catcheck.reminder.domain.port.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A1 ({@code docs/cat-rag-credit-pipeline.md} mục A4-(1)) — cổng lịch nhắc đang bật cho {@code ai}. */
class ActiveReminderQueryAdapterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CAT_ID = UUID.randomUUID();
    private static final Instant NEXT_RUN = Instant.parse("2026-10-11T07:00:00Z");

    @Test
    @DisplayName("Map type/nextRunAt thô và chuyển đúng bộ lọc active=true xuống repository")
    void mapsActiveRemindersAndForwardsActiveFilter() {
        RecordingReminderRepository repository = new RecordingReminderRepository();
        repository.rows = List.of(reminder(ReminderType.SCAN_ROUTINE, NEXT_RUN));
        ActiveReminderQueryAdapter adapter = new ActiveReminderQueryAdapter(repository);

        List<ActiveReminderQuery.ActiveReminder> reminders = adapter.activeRemindersFor(USER_ID, CAT_ID);

        assertThat(reminders).hasSize(1);
        assertThat(reminders.getFirst().type()).isEqualTo("SCAN_ROUTINE");
        assertThat(reminders.getFirst().nextRunAt()).isEqualTo(NEXT_RUN);
        assertThat(repository.lastUserId).isEqualTo(USER_ID);
        assertThat(repository.lastCatId).isEqualTo(CAT_ID);
        assertThat(repository.lastActiveFilter).isTrue();
    }

    @Test
    @DisplayName("Không có lịch nhắc nào ⇒ danh sách rỗng, không NPE")
    void noRemindersReturnsEmptyList() {
        RecordingReminderRepository repository = new RecordingReminderRepository();
        repository.rows = List.of();
        ActiveReminderQueryAdapter adapter = new ActiveReminderQueryAdapter(repository);

        assertThat(adapter.activeRemindersFor(USER_ID, CAT_ID)).isEmpty();
    }

    private static Reminder reminder(ReminderType type, Instant nextRunAt) {
        return new Reminder(UUID.randomUUID(), USER_ID, CAT_ID, type, ScheduleKind.INTERVAL, 1, null,
                null, null, "Asia/Ho_Chi_Minh", nextRunAt, null, null, List.of("PUSH"),
                ReminderSource.USER, true, NEXT_RUN.minusSeconds(3600), NEXT_RUN.minusSeconds(3600));
    }

    private static final class RecordingReminderRepository implements ReminderRepository {

        private List<Reminder> rows = List.of();
        private UUID lastUserId;
        private UUID lastCatId;
        private Boolean lastActiveFilter;

        @Override
        public Reminder insert(Reminder reminder) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public Optional<Reminder> findByIdForUser(UUID id, UUID userId) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public List<Reminder> findAllForUser(UUID userId, UUID catIdFilter, Boolean activeFilter) {
            this.lastUserId = userId;
            this.lastCatId = catIdFilter;
            this.lastActiveFilter = activeFilter;
            return rows;
        }

        @Override
        public Reminder update(Reminder reminder) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public void softDelete(UUID id, UUID userId) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public Optional<UUID> findActiveIdByCatAndType(UUID userId, UUID catId, String type) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public List<UUID> findDueIds(Instant now, int limit) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public Optional<Reminder> lockDue(UUID id, Instant now) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }

        @Override
        public void markDispatched(UUID id, Instant sentAt, Instant nextRunAt) {
            throw new UnsupportedOperationException("not used by ActiveReminderQueryAdapter");
        }
    }
}
