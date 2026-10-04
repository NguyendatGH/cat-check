package com.catcheck.export.application;

import com.catcheck.export.api.ExportErrorCode;
import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.ExportStatus;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>Hồi quy H15.76 — {@code POST /exports} không được kẹt ở {@code QUEUED}.</b>
 *
 * <p>Bản lỗi gọi {@code exportExecutor.execute(...)} ngay trong {@code @Transactional}: worker
 * đọc {@code export_job} trước khi {@code INSERT} commit, không thấy dòng nào, bỏ chạy. Dòng ở
 * lại {@code QUEUED} vĩnh viễn và — vì p4 G1 I25 chỉ cho một job
 * {@code QUEUED}/{@code RUNNING} mỗi user — khoá luôn mọi lần export sau bằng
 * {@code 409 EXPORT_JOB_IN_PROGRESS}.</p>
 *
 * <p>Test dựng lại đúng ranh giới đó bằng {@code TransactionSynchronizationManager}: trong khi
 * "transaction" còn mở, worker <b>chưa được</b> chạy; chỉ sau {@code afterCommit} nó mới chạy,
 * và lúc đó dòng đã đọc được.</p>
 */
class ExportRequestServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant NOW = Instant.parse("2026-10-03T04:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeExportJobRepository repository = new FakeExportJobRepository();
    private final List<Runnable> submitted = new ArrayList<>();

    /** Chạy ngay khi được submit — thứ tự trong test vì thế là thứ tự thật, không phải tình cờ. */
    private final TaskExecutor directExecutor = command -> {
        submitted.add(command);
        command.run();
    };

    private final ExportGenerationService generationService = new ExportGenerationService(
            repository,
            ExportTestDoubles.catOwnedBy(CAT_ID, USER_ID),
            ExportTestDoubles.oneScanAt(NOW.minusSeconds(3600)),
            ExportTestDoubles.noFlags(),
            ExportTestDoubles.pdfOf(2048, 3),
            new ExportTestDoubles.InMemoryReportStorage(),
            clock);

    private final ExportRequestService requestService = new ExportRequestService(
            repository,
            ExportTestDoubles.catOwnedBy(CAT_ID, USER_ID),
            ExportTestDoubles.oneScanAt(NOW.minusSeconds(3600)),
            new DocumentCodeGenerator(repository, clock),
            new ExportJobDispatcher(directExecutor, generationService, clock),
            new UuidV7(clock),
            clock);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("H15.76 — job đi hết QUEUED → RUNNING → READY, worker chỉ chạy SAU commit")
    void jobReachesReadyAndTheWorkerOnlyRunsAfterCommit() {
        beginTransaction();
        ExportJob job = requestService.request(command());

        assertThat(job.getStatus()).isEqualTo(ExportStatus.QUEUED);
        assertThat(submitted)
                .as("worker KHONG duoc chay khi transaction con mo (H15.76)")
                .isEmpty();

        commitTransaction();

        assertThat(submitted).hasSize(1);
        assertThat(repository.statusTrail)
                .as("p13 §13.6.2 luong trang thai")
                .containsExactly("QUEUED", "RUNNING", "READY");

        ExportJob done = repository.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(ExportStatus.READY);
        assertThat(done.getFileBytes()).isEqualTo(2048L);
        assertThat(done.getPageCount()).isEqualTo(3);
        assertThat(done.getExpiresAt())
                .as("p13 §13.6.4: PDF giu toi da 7 ngay ke tu ready_at")
                .isEqualTo(NOW.plusSeconds(7L * 24 * 3600));
    }

    @Test
    @DisplayName("H15.76 — export lần hai KHÔNG bị 409 sau khi lần một đã xong")
    void aSecondExportIsNotBlockedOnceTheFirstFinished() {
        beginTransaction();
        requestService.request(command());
        commitTransaction();

        beginTransaction();
        ExportJob second = requestService.request(command());
        assertThat(second.getStatus())
                .as("lan hai KHONG bi 409: tao duoc dong moi o QUEUED")
                .isEqualTo(ExportStatus.QUEUED);
        commitTransaction();

        assertThat(repository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(ExportStatus.READY);
        assertThat(repository.statusTrail)
                .as("hai lan xuat, moi lan di het QUEUED -> RUNNING -> READY")
                .containsExactly("QUEUED", "RUNNING", "READY", "QUEUED", "RUNNING", "READY");
    }

    @Test
    @DisplayName("Bất biến I25 vẫn còn hiệu lực: job đang chạy dở thì lần hai trả 409")
    void aSecondExportIsRejectedWhileTheFirstIsStillActive() {
        beginTransaction();
        requestService.request(command());
        // KHÔNG commit: dòng thứ nhất còn QUEUED.

        assertThatThrownBy(() -> requestService.request(command()))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExportErrorCode.EXPORT_JOB_IN_PROGRESS);
    }

    // ------------------------------------------------------------------ dàn dựng

    private ExportRequestCommand command() {
        return new ExportRequestCommand(USER_ID, CAT_ID, LocalDate.of(2026, 9, 4),
                LocalDate.of(2026, 10, 3), "30D", List.of("TREND", "SCAN_LOG"), "vi",
                "Asia/Ho_Chi_Minh");
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private void commitTransaction() {
        TransactionSynchronizationUtils.triggerBeforeCommit(false);
        TransactionSynchronizationUtils.triggerBeforeCompletion();
        TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        TransactionSynchronizationManager.clearSynchronization();
    }
}
