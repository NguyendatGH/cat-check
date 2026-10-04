package com.catcheck.export.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

/**
 * Đẩy một {@code export_job} vừa tạo sang bể luồng nền — <b>chỉ sau khi transaction đã COMMIT</b>.
 *
 * <p><b>Bug đã sửa (H15.76).</b> {@code ExportRequestService.request()} là {@code @Transactional};
 * gọi thẳng {@code executor.execute(...)} ở trong đó thì worker chạy song song với transaction
 * còn đang mở và gần như luôn thắng cuộc đua: nó {@code SELECT export_job} trước khi dòng
 * {@code INSERT} được commit, không thấy gì, log {@code "export_job ... bien mat truoc khi xu
 * ly"} rồi thoát. Dòng vừa ghi ở lại {@code QUEUED} vĩnh viễn — và vì p4 G1 có
 * {@code UNIQUE(user_id) WHERE status IN ('QUEUED','RUNNING')} (bất biến I25), nó khoá luôn mọi
 * lần export sau của user đó bằng {@code 409 EXPORT_JOB_IN_PROGRESS}.</p>
 *
 * <p>Vì sao {@code TransactionSynchronization.afterCommit} chứ không
 * {@code @TransactionalEventListener(AFTER_COMMIT)}: cả hai chạy ở đúng một thời điểm, nhưng
 * cách này không cần thêm một kiểu event công khai cho việc hoàn toàn nội bộ module, và giữ
 * nguyên quan hệ gọi trực tiếp nên đọc code lần theo được. Việc gì cần bền vững qua sự cố thì
 * đã có {@code ReportPdfTimeoutJob} dọn, xem javadoc ở đó.</p>
 *
 * <p>Không có transaction đang mở (job nền gọi lại, test gọi thẳng) thì chạy ngay — không im
 * lặng bỏ qua.</p>
 */
@Component
public class ExportJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExportJobDispatcher.class);

    private final TaskExecutor exportExecutor;
    private final ExportGenerationService generationService;
    private final Clock clock;

    public ExportJobDispatcher(
            @Qualifier("exportExecutor") TaskExecutor exportExecutor,
            ExportGenerationService generationService,
            Clock clock) {
        this.exportExecutor = exportExecutor;
        this.generationService = generationService;
        this.clock = clock;
    }

    /**
     * Hẹn chạy job sau khi transaction hiện tại commit. Gọi được nhiều lần cho nhiều job trong
     * cùng một transaction.
     */
    public void dispatchAfterCommit(UUID jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(jobId);
            }
        });
    }

    private void submit(UUID jobId) {
        try {
            exportExecutor.execute(() -> generationService.generate(jobId));
        } catch (TaskRejectedException rejected) {
            // Hàng đợi đầy: KHÔNG để dòng ở lại QUEUED im lặng, vì nó chặn mọi export sau của
            // user (I25). Đánh hỏng ngay tại đây để user thấy lý do và bấm lại được.
            log.warn("export_job {} bi tu choi khoi hang doi: {}", jobId, rejected.getMessage());
            generationService.markRejected(jobId, clock.instant());
        }
    }
}
