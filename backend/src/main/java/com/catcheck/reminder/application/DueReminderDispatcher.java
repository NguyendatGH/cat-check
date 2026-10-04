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

/**
 * Xử lý <b>một</b> lịch nhắc tới hạn — phần nghiệp vụ của {@code SendDueRemindersJob}.
 *
 * <p><b>Vì sao mỗi lịch một transaction {@code REQUIRES_NEW} thay vì khoá cả lô trong một
 * transaction dài:</b> p12 §12.6.3 đòi "lỗi một reminder không chặn batch". Nếu cả lô nằm
 * trong một transaction thì ngoại lệ ở lịch thứ ba đánh dấu transaction
 * {@code rollback-only} và hai lịch đầu — đã enqueue thành công — cũng mất theo. Mỗi lịch một
 * transaction riêng khiến "bỏ một, giữ phần còn lại" là hành vi mặc định chứ không phải thứ
 * phải nhớ viết đúng.</p>
 *
 * <p><b>Và vì sao vẫn đúng với p12 §12.5.5(b)</b> ("cập nhật {@code next_run_at} trong cùng
 * transaction với việc ghi outbox"): {@code NotificationService.enqueue} là
 * {@code @Transactional} mặc định {@code REQUIRED} nên nó <i>tham gia</i> transaction này, không
 * mở cái mới. Ghi {@code notification} + {@code notification_outbox} + {@code email_outbox} và
 * {@code UPDATE reminder} commit cùng lúc, nên không có cửa sổ nào mà thông báo đã đi mà
 * {@code next_run_at} còn ở quá khứ.</p>
 *
 * <p><b>Giờ im lặng không xử lý ở đây</b> (p12 §12.5.3). Khung giờ im lặng nằm ở
 * {@code user_notification_preference} — bảng của module {@code notification} — và
 * {@code NotificationService} đã hoãn push/email bằng {@code scheduled_at}/
 * {@code next_attempt_at} đúng luật "hoãn chứ không huỷ" cho mọi template có
 * {@code quietHoursApply}. Lặp lại phép tính đó ở đây bằng cách dời {@code next_run_at} sẽ
 * hoãn <b>hai lần</b> cùng một thông báo. Xem handoff H15.88.</p>
 */
@Service
public class DueReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DueReminderDispatcher.class);

    /**
     * Mốc chuyển từ {@code REMINDER_SCAN_DUE} sang {@code REMINDER_OVERDUE} — p12 §12.2.2:
     * "quá {@code next_run_at} một khoảng cấu hình (đề xuất +24h) mà chưa quét".
     */
    static final Duration OVERDUE_AFTER = Duration.ofHours(24);

    /** Mã template p12 §12.2.2. KHÔNG tự đặt mã mới — registry của p12 là nguồn duy nhất. */
    static final String TEMPLATE_SCAN_DUE = "REMINDER_SCAN_DUE";

    /** Mã template p12 §12.2.2. */
    static final String TEMPLATE_OVERDUE = "REMINDER_OVERDUE";

    /** {@code notification.ref_type} — enum của module notification, truyền bằng chuỗi (R6). */
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

    /** Lô id tới hạn — đọc thuần, không khoá dòng (xem javadoc {@code findDueIds}). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<UUID> findDue(Instant now, int limit) {
        return repository.findDueIds(now, limit);
    }

    /**
     * Khoá — gửi — tính lại lịch, tất cả trong một transaction.
     *
     * @return {@code true} khi đã xếp hàng một thông báo; {@code false} khi lịch bị instance
     *         khác lấy mất, đã bị tắt/xoá, hoặc hồ sơ mèo không còn (p12 §12.5.5)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean dispatch(UUID reminderId, Instant now) {
        Optional<Reminder> locked = repository.lockDue(reminderId, now);
        if (locked.isEmpty()) {
            return false;
        }
        Reminder reminder = locked.get();

        Optional<String> catName = cats.findAliveCatName(reminder.catId(), reminder.userId());
        if (catName.isEmpty()) {
            // Hồ sơ mèo đã xoá mềm mà lịch chưa được tắt theo (p12 §12.5.5 đòi tắt trong cùng
            // transaction với việc xoá mèo). Không gửi "nhắc quét cho con mèo không còn tồn
            // tại"; đẩy next_run_at đi để job không quay lại dòng này mỗi 5 phút.
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
        // KHÔNG log tên mèo / userId kèm nội dung — p15 §15.2.3 + quy tắc "không log PII".
        log.debug("Da xep hang {} cho reminder {}", template, reminder.id());
        return true;
    }

    /**
     * "Quá hạn" đo bằng {@code last_satisfied_at}, KHÔNG bằng {@code last_run_at} — p12 §12.5.1:
     * job có thể đã nhắc nhiều lần mà người dùng vẫn chưa quét, nên mốc gửi nhắc không nói lên
     * điều gì về việc lịch đã được thoả hay chưa.
     */
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

    /**
     * Nội dung nguyên văn p12 §12.2.2. §12.2.1 cấm nêu pH/phân loại/mức độ bất thường trong
     * banner — ở đây chỉ có tên mèo, đúng cột "Ví dụ ĐÚNG" của bảng đó.
     */
    private static String body(boolean overdue, String catName) {
        return overdue
                ? catName + " chưa được quét theo lịch. Quét ngay để không bỏ lỡ thay đổi."
                : "Đến lịch quét cho " + catName + " hôm nay.";
    }

    /** {@code deepLink} phải là route thật của p9 §9.4 — p12 §12.2.7 bắt buộc rà. */
    private static Map<String, Object> payload(Reminder reminder, String catName) {
        return Map.of(
                "catId", reminder.catId().toString(),
                "catName", catName,
                "reminderId", reminder.id().toString(),
                "deepLink", "/scan?catId=" + reminder.catId());
    }

    /**
     * Quy ước p12 §12.4: {@code <template>:<refType>:<refId>:<mốc>}. Mốc là {@code next_run_at}
     * của lần tới hạn này, nên hai instance cùng xử lý một lịch (hoặc một lần chạy lại sau sự
     * cố) va đúng {@code uq_notification_dedupe} thay vì gửi hai lần.
     */
    private static String dedupeKey(String template, Reminder reminder) {
        return template + ":REMINDER:" + reminder.id() + ':' + reminder.nextRunAt().getEpochSecond();
    }
}
