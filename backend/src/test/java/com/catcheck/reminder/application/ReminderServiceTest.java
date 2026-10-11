package com.catcheck.reminder.application;

import com.catcheck.reminder.api.ReminderErrorCode;
import com.catcheck.reminder.application.spi.CatOwnershipPort;
import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.ReminderSource;
import com.catcheck.reminder.domain.ReminderType;
import com.catcheck.reminder.domain.ScheduleKind;
import com.catcheck.reminder.domain.port.ReminderRepository;
import com.catcheck.shared.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regression coverage for the partial unique index race on active cat reminders. */
class ReminderServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID REMINDER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant NOW = Instant.parse("2026-10-03T04:00:00Z");

    private final RacingReminderRepository repository = new RacingReminderRepository();
    private final ReminderService service = new ReminderService(
            repository, new OwnedCat(), userId -> true, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void concurrentDuplicateInsertIsReportedAsConflict() {
        repository.failure = new DuplicateKeyException("uq_reminder_cat_type_active");

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReminderErrorCode.REMINDER_LIMIT_REACHED);
    }

    @Test
    void concurrentReactivationIsReportedAsConflict() {
        // Bật lại lịch đang tắt trong lúc request khác vừa tạo lịch cùng cat+type: cả hai qua được
        // findActiveIdByCatAndType rồi va uq_reminder_cat_type_active ở UPDATE.
        repository.stored = inactiveReminder();
        repository.failure = new DuplicateKeyException("uq_reminder_cat_type_active");

        assertThatThrownBy(() -> service.patch(new ReminderCommands.Patch(
                USER_ID, REMINDER_ID, null, null, null, null, null, null, true)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReminderErrorCode.REMINDER_LIMIT_REACHED);
    }

    @Test
    void otherIntegrityViolationsAreNotMislabelledAsLimitReached() {
        // Ví dụ FK: hồ sơ mèo bị xoá cứng giữa lúc kiểm và lúc INSERT — không phải "đã có lịch".
        repository.failure = new DataIntegrityViolationException("fk_reminder_cat");

        assertThatThrownBy(() -> service.create(command()))
                .isNotInstanceOf(ConflictException.class)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ReminderCommands.Create command() {
        return new ReminderCommands.Create(USER_ID, CAT_ID, ReminderType.SCAN_ROUTINE.name(), "INTERVAL",
                7, null, LocalTime.of(9, 0), null, "Asia/Ho_Chi_Minh", List.of("PUSH"), "USER");
    }

    private static Reminder inactiveReminder() {
        return new Reminder(REMINDER_ID, USER_ID, CAT_ID, ReminderType.SCAN_ROUTINE, ScheduleKind.INTERVAL,
                7, null, LocalTime.of(9, 0), null, "Asia/Ho_Chi_Minh", null, null, null,
                List.of("PUSH"), ReminderSource.USER, false, NOW, NOW);
    }

    private static final class OwnedCat implements CatOwnershipPort {
        @Override
        public boolean isOwnedAndAlive(UUID catId, UUID ownerId) {
            return CAT_ID.equals(catId) && USER_ID.equals(ownerId);
        }

        @Override
        public Optional<String> findAliveCatName(UUID catId, UUID ownerId) {
            return Optional.of("Miu");
        }
    }

    /** Mọi lần ghi ném {@link #failure} — mô phỏng request đua đã commit trước. */
    private static final class RacingReminderRepository implements ReminderRepository {
        private RuntimeException failure;
        private Reminder stored;

        @Override
        public Reminder insert(Reminder reminder) {
            throw failure;
        }

        @Override
        public Reminder update(Reminder reminder) {
            throw failure;
        }

        @Override public Optional<Reminder> findByIdForUser(UUID id, UUID userId) {
            return Optional.ofNullable(stored).filter(r -> r.id().equals(id) && r.userId().equals(userId));
        }
        @Override public List<Reminder> findAllForUser(UUID userId, UUID catId, Boolean active) { return List.of(); }
        @Override public void softDelete(UUID id, UUID userId) { throw new UnsupportedOperationException(); }
        @Override public Optional<UUID> findActiveIdByCatAndType(UUID userId, UUID catId, String type) {
            return Optional.empty();
        }
        @Override public List<UUID> findDueIds(Instant now, int limit) { return List.of(); }
        @Override public Optional<Reminder> lockDue(UUID id, Instant now) { return Optional.empty(); }
        @Override public void markDispatched(UUID id, Instant sentAt, Instant nextRunAt) {
            throw new UnsupportedOperationException();
        }
    }
}
