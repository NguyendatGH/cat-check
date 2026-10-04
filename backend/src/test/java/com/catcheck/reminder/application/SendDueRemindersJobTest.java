package com.catcheck.reminder.application;

import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.reminder.application.spi.CatOwnershipPort;
import com.catcheck.reminder.domain.Reminder;
import com.catcheck.reminder.domain.ReminderSource;
import com.catcheck.reminder.domain.ReminderType;
import com.catcheck.reminder.domain.ScheduleKind;
import com.catcheck.reminder.domain.port.ReminderRepository;
import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import com.catcheck.shared.job.application.JobRunRecorder;
import com.catcheck.shared.job.application.JobRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code SendDueRemindersJob} — p12 §12.6.3 nhóm B.
 *
 * <p>Ba điều bài test này canh, theo đúng thứ tự quan trọng:</p>
 * <ol>
 *   <li><b>Tới hạn thì gửi đúng MỘT lần.</b> Trước W2-B tính năng nhắc nhở có đủ 6 endpoint
 *       I1–I6 nhưng không job nào gửi, nên "gửi được" là yêu cầu số một.</li>
 *   <li><b>{@code next_run_at} nhảy đúng chu kỳ theo múi giờ của lịch</b> (p12 §12.5.2,
 *       §12.5.4) — tính bằng UTC sẽ lệch 7 giờ và người dùng nhận nhắc lúc 1 giờ sáng.</li>
 *   <li><b>Chạy lại không gửi trùng</b> (p12 §12.5.5): đây là thứ duy nhất trong ba thứ mà
 *       một lần test tay không bắt được, vì nó chỉ sai khi job chạy vòng thứ hai.</li>
 * </ol>
 */
class SendDueRemindersJobTest {

    /** 08:00 giờ Việt Nam — chọn mốc tròn để con số kỳ vọng đọc được bằng mắt. */
    private static final Instant NOW = Instant.parse("2026-10-03T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");

    private final FakeReminders reminders = new FakeReminders();
    private final FakeCats cats = new FakeCats();
    private final RecordingGateway gateway = new RecordingGateway();
    private final FakeJobRuns jobRuns = new FakeJobRuns();

    private final SendDueRemindersJob job = new SendDueRemindersJob(
            new JobRunner(new JobRunRecorder(jobRuns, new UuidV7(CLOCK), CLOCK),
                    properties(), new SimpleMeterRegistry(), CLOCK),
            new DueReminderDispatcher(reminders, cats, gateway),
            properties());

    @Test
    void dueReminderIsEnqueuedOnceAndNextRunAdvancesInTheRemindersTimezone() {
        Reminder due = reminders.add(scanRoutine(NOW.minusSeconds(60), NOW.minusSeconds(60)));

        job.run();

        assertEquals(1, gateway.requests.size(), "dung mot thong bao cho mot lich den han");
        NotificationRequest request = gateway.requests.getFirst();
        assertEquals("REMINDER_SCAN_DUE", request.templateCode());
        assertEquals("Đến lịch quét định kỳ", request.title());
        assertEquals("Đến lịch quét cho Mochi hôm nay.", request.body());
        assertEquals("REMINDER", request.refType());
        assertEquals(due.id(), request.refId());
        assertEquals("/scan?catId=" + due.catId(), request.payload().get("deepLink"));

        // 03/10 giờ ICT + 3 ngày, tại 08:00 ICT = 06/10 01:00 UTC. Nếu ai đó tính bằng UTC thì
        // kết quả sẽ là 06/10 08:00Z — lệch 7 tiếng, và test này đỏ.
        assertEquals(
                java.time.LocalDate.of(2026, 10, 6).atTime(LocalTime.of(8, 0)).atZone(ICT).toInstant(),
                reminders.byId(due.id()).nextRunAt());
        assertEquals(NOW, reminders.byId(due.id()).lastRunAt());
    }

    @Test
    void theJobRunIsRecordedWithTheRightCounters() {
        reminders.add(scanRoutine(NOW.minusSeconds(60), NOW.minusSeconds(60)));

        job.run();

        FakeJobRuns.Row row = jobRuns.only();
        assertEquals("SendDueRemindersJob", row.jobName());
        assertEquals(JobTriggerType.SCHEDULE, row.triggerType());
        assertEquals(JobRunStatus.SUCCESS, row.status());
        assertEquals(1, row.itemsProcessed());
        assertEquals(0, row.itemsFailed());
        assertEquals(NOW, row.finishedAt());
    }

    @Test
    void runningAgainDoesNotResendBecauseNextRunMovedIntoTheFuture() {
        reminders.add(scanRoutine(NOW.minusSeconds(60), NOW.minusSeconds(60)));

        job.run();
        job.run();

        assertEquals(1, gateway.requests.size(), "lan chay thu hai KHONG duoc gui trung");
        assertEquals(2, jobRuns.rows.size(), "van ghi mot dong job_run moi lan chay");
        assertEquals(0, jobRuns.rows.get(1).itemsProcessed(), "vong hai khong con lich den han");
    }

    @Test
    void lateByMoreThanADayWithoutAScanUsesTheOverdueTemplate() {
        // Tới hạn 30 giờ trước và lần quét thoả lịch gần nhất còn cũ hơn thế ⇒ quá hạn.
        Instant due = NOW.minus(Duration.ofHours(30));
        reminders.add(scanRoutine(due, due.minus(Duration.ofDays(1))));

        job.run();

        assertEquals("REMINDER_OVERDUE", gateway.requests.getFirst().templateCode());
        assertEquals("Đã quá hạn theo dõi", gateway.requests.getFirst().title());
    }

    @Test
    void lateButAlreadyScannedStaysOnTheNormalTemplate() {
        // p12 §12.5.1: "quá hạn" đo bằng last_satisfied_at, KHÔNG bằng last_run_at. Người dùng
        // đã quét sau mốc tới hạn thì không phải người quên lịch.
        Instant due = NOW.minus(Duration.ofHours(30));
        reminders.add(scanRoutine(due, due.plus(Duration.ofHours(1))));

        job.run();

        assertEquals("REMINDER_SCAN_DUE", gateway.requests.getFirst().templateCode());
    }

    @Test
    void creditExpiryAndSurveyRemindersAreLeftToOtherJobs() {
        // p12 §12.5.1: CREDIT_EXPIRY do CreditExpiringReminderJob lo (mốc tính từ
        // credit_batch.expires_at). SURVEY_FOLLOWUP chưa có mã template nào trong registry
        // p12 §12.2 — xem handoff H15.89.
        reminders.add(withType(ReminderType.CREDIT_EXPIRY, NOW.minusSeconds(60)));
        reminders.add(withType(ReminderType.SURVEY_FOLLOWUP, NOW.minusSeconds(60)));

        JobOutcome outcome = job.sendDue(
                new com.catcheck.shared.job.JobContext(UUID.randomUUID(), "SendDueRemindersJob", NOW, false));

        assertEquals(0, outcome.itemsProcessed());
        assertTrue(gateway.requests.isEmpty());
    }

    @Test
    void aDeletedCatProfileIsSkippedButTheScheduleStillMovesOn() {
        Reminder orphan = reminders.add(scanRoutine(NOW.minusSeconds(60), NOW.minusSeconds(60)));
        cats.names.clear();

        job.run();

        assertTrue(gateway.requests.isEmpty(), "khong nhac quet cho con meo khong con ton tai");
        // Đẩy lịch đi, nếu không job quay lại đúng dòng này mỗi 5 phút mãi mãi.
        assertTrue(reminders.byId(orphan.id()).nextRunAt().isAfter(NOW));
        assertNull(reminders.byId(orphan.id()).lastRunAt(), "chua gui thi khong dat last_run_at");
    }

    @Test
    void oneFailingReminderDoesNotStopTheRestOfTheBatch() {
        Reminder broken = reminders.add(scanRoutine(NOW.minusSeconds(120), NOW.minusSeconds(120)));
        reminders.add(scanRoutine(NOW.minusSeconds(60), NOW.minusSeconds(60)));
        gateway.failFor = broken.id();

        job.run();

        assertEquals(1, gateway.requests.size(), "lich con lai van duoc gui");
        FakeJobRuns.Row row = jobRuns.only();
        assertEquals(JobRunStatus.PARTIAL, row.status(), "p12 §12.6.2: mot item loi => PARTIAL");
        assertEquals(2, row.itemsProcessed());
        assertEquals(1, row.itemsFailed());
    }

    @Test
    void dryRunCountsWithoutEnqueueing() {
        reminders.add(scanRoutine(NOW.minusSeconds(60), NOW.minusSeconds(60)));

        JobOutcome outcome = job.sendDue(new com.catcheck.shared.job.JobContext(
                UUID.randomUUID(), "SendDueRemindersJob", NOW, true));

        assertEquals(1, outcome.itemsProcessed());
        assertTrue(gateway.requests.isEmpty());
    }

    /* ------------------------------------------------------------------------ dựng dữ liệu */

    private Reminder scanRoutine(Instant nextRunAt, Instant lastSatisfiedAt) {
        return new Reminder(
                null, UUID.randomUUID(), UUID.randomUUID(), ReminderType.SCAN_ROUTINE,
                ScheduleKind.INTERVAL, 3, null, LocalTime.of(8, 0), LocalTime.of(10, 0),
                ICT.getId(), nextRunAt, null, lastSatisfiedAt, List.of("PUSH", "IN_APP"),
                ReminderSource.USER, true, NOW, NOW);
    }

    private Reminder withType(ReminderType type, Instant nextRunAt) {
        Reminder base = scanRoutine(nextRunAt, null);
        return new Reminder(base.id(), base.userId(), base.catId(), type, base.scheduleKind(),
                base.intervalDays(), base.rrule(), base.preferredTimeStart(),
                base.preferredTimeEnd(), base.timezone(), base.nextRunAt(), base.lastRunAt(),
                base.lastSatisfiedAt(), base.channels(), base.source(), base.active(),
                base.createdAt(), base.updatedAt());
    }

    private static JobProperties properties() {
        JobProperties.JobSetting onlyThisJob =
                new JobProperties.JobSetting(true, "0 */5 * * * *", Duration.ofSeconds(270), 200, 1_000, false);
        return new JobProperties(true, ICT, onlyThisJob, onlyThisJob, onlyThisJob,
                onlyThisJob, onlyThisJob, onlyThisJob, onlyThisJob, onlyThisJob);
    }

    /* ------------------------------------------------------------------------------- fake */

    /**
     * Bảng {@code reminder} trong bộ nhớ. Hai điều cố ý mô phỏng đúng SQL thật, vì chúng là
     * hợp đồng chứ không phải chi tiết: {@code findDueIds} lọc {@code type = 'SCAN_ROUTINE'}
     * và {@code lockDue} từ chối dòng có {@code next_run_at} đã ở tương lai.
     */
    private static final class FakeReminders implements ReminderRepository {

        private final Map<UUID, Reminder> rows = new LinkedHashMap<>();

        Reminder add(Reminder reminder) {
            UUID id = UUID.randomUUID();
            Reminder stored = new Reminder(id, reminder.userId(), reminder.catId(), reminder.type(),
                    reminder.scheduleKind(), reminder.intervalDays(), reminder.rrule(),
                    reminder.preferredTimeStart(), reminder.preferredTimeEnd(), reminder.timezone(),
                    reminder.nextRunAt(), reminder.lastRunAt(), reminder.lastSatisfiedAt(),
                    reminder.channels(), reminder.source(), reminder.active(),
                    reminder.createdAt(), reminder.updatedAt());
            rows.put(id, stored);
            return stored;
        }

        Reminder byId(UUID id) {
            return rows.get(id);
        }

        @Override
        public List<UUID> findDueIds(Instant now, int limit) {
            return rows.values().stream()
                    .filter(Reminder::active)
                    .filter(row -> row.type() == ReminderType.SCAN_ROUTINE)
                    .filter(row -> row.nextRunAt() != null && !row.nextRunAt().isAfter(now))
                    .sorted((a, b) -> a.nextRunAt().compareTo(b.nextRunAt()))
                    .limit(limit)
                    .map(Reminder::id)
                    .toList();
        }

        @Override
        public Optional<Reminder> lockDue(UUID id, Instant now) {
            return Optional.ofNullable(rows.get(id))
                    .filter(Reminder::active)
                    .filter(row -> row.nextRunAt() != null && !row.nextRunAt().isAfter(now));
        }

        @Override
        public void markDispatched(UUID id, Instant sentAt, Instant nextRunAt) {
            Reminder row = rows.get(id);
            rows.put(id, new Reminder(row.id(), row.userId(), row.catId(), row.type(),
                    row.scheduleKind(), row.intervalDays(), row.rrule(), row.preferredTimeStart(),
                    row.preferredTimeEnd(), row.timezone(), nextRunAt, sentAt,
                    row.lastSatisfiedAt(), row.channels(), row.source(), row.active(),
                    row.createdAt(), row.updatedAt()));
        }

        @Override
        public Reminder insert(Reminder reminder) {
            return add(reminder);
        }

        @Override
        public Optional<Reminder> findByIdForUser(UUID id, UUID userId) {
            return Optional.ofNullable(rows.get(id)).filter(row -> row.userId().equals(userId));
        }

        @Override
        public List<Reminder> findAllForUser(UUID userId, UUID catIdFilter, Boolean activeFilter) {
            return rows.values().stream().filter(row -> row.userId().equals(userId)).toList();
        }

        @Override
        public Reminder update(Reminder reminder) {
            rows.put(reminder.id(), reminder);
            return reminder;
        }

        @Override
        public void softDelete(UUID id, UUID userId) {
            rows.remove(id);
        }

        @Override
        public Optional<UUID> findActiveIdByCatAndType(UUID userId, UUID catId, String type) {
            return Optional.empty();
        }
    }

    /** Mọi mèo đều tên "Mochi" và đều còn sống, trừ khi test xoá {@link #names}. */
    private static final class FakeCats implements CatOwnershipPort {

        final Map<String, String> names = new LinkedHashMap<>(Map.of("any", "Mochi"));

        @Override
        public boolean isOwnedAndAlive(UUID catId, UUID ownerId) {
            return !names.isEmpty();
        }

        @Override
        public Optional<String> findAliveCatName(UUID catId, UUID ownerId) {
            return Optional.ofNullable(names.get("any"));
        }
    }

    /** Cổng thông báo ghi lại mọi yêu cầu; {@link #failFor} ép một lịch ném lỗi. */
    private static final class RecordingGateway implements NotificationGateway {

        final List<NotificationRequest> requests = new ArrayList<>();
        UUID failFor;

        @Override
        public void enqueue(NotificationRequest request) {
            if (failFor != null && failFor.equals(request.refId())) {
                throw new IllegalStateException("gia lap loi mot thong bao");
            }
            requests.add(request);
        }

        @Override
        public void enqueueTransactionalEmail(TransactionalEmailRequest request) {
            throw new UnsupportedOperationException("job nhac khong gui email giao dich");
        }

        @Override
        public int revokePushOnConsentWithdrawal(UUID userId) {
            throw new UnsupportedOperationException("khong lien quan tới job nhac");
        }
    }

    /** {@code job_run} trong bộ nhớ — đủ để kiểm "đúng một dòng mỗi lần chạy". */
    private static final class FakeJobRuns implements JobRunPort {

        record Row(UUID runId, String jobName, JobTriggerType triggerType, Instant startedAt,
                   JobRunStatus status, Integer itemsProcessed, Integer itemsFailed,
                   Instant finishedAt) {
        }

        final List<Row> rows = new ArrayList<>();

        @Override
        public void insertStarted(UUID runId, String jobName, JobTriggerType triggerType,
                                  boolean dryRun, Instant startedAt) {
            rows.add(new Row(runId, jobName, triggerType, startedAt, JobRunStatus.RUNNING,
                    null, null, null));
        }

        @Override
        public void finish(UUID runId, JobOutcome outcome, Instant finishedAt) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).runId().equals(runId)) {
                    Row open = rows.get(i);
                    rows.set(i, new Row(open.runId(), open.jobName(), open.triggerType(),
                            open.startedAt(), outcome.status(), outcome.itemsProcessed(),
                            outcome.itemsFailed(), finishedAt));
                    return;
                }
            }
            throw new IllegalStateException("khong tim thay dong job_run: " + runId);
        }

        @Override
        public Optional<Instant> findLastStartedAt(String jobName) {
            return rows.stream().filter(row -> row.jobName().equals(jobName))
                    .map(Row::startedAt).max(Instant::compareTo);
        }

        Row only() {
            if (rows.size() != 1) {
                throw new IllegalStateException("mong doi dung 1 dong job_run, co " + rows.size());
            }
            return rows.getFirst();
        }
    }
}
