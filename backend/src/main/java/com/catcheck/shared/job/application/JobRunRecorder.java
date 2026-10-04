package com.catcheck.shared.job.application;

import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunPort;
import com.catcheck.shared.job.JobTriggerType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Mở và đóng đúng <b>một dòng {@code job_run}</b> cho mỗi lần chạy job (p12 §12.6.1 quy tắc 4).
 *
 * <p><b>{@code Propagation.REQUIRES_NEW} là điểm mấu chốt, không phải chi tiết.</b> Thân job
 * thường mở transaction riêng cho từng item và có thể rollback; nếu dòng {@code job_run} nằm
 * trong cùng transaction đó thì một lần chạy thất bại sẽ <i>biến mất khỏi nhật ký</i> — đúng lần
 * chạy mà người vận hành cần nhìn thấy nhất. Mở/đóng ở transaction độc lập khiến bản ghi tồn tại
 * bất kể nghiệp vụ bên trong thành hay bại.</p>
 *
 * <p>Hệ quả có chủ ý: dòng còn {@code status = 'RUNNING'} và {@code finished_at IS NULL} sau khi
 * tiến trình chết giữa chừng là <b>tín hiệu đúng</b>, không phải rác — p4 §K3 ghi rõ
 * "{@code finished_at} NULL = đang chạy HOẶC đã chết giữa chừng", và index
 * {@code idx_job_run_running} tồn tại để tìm đúng những dòng đó.</p>
 */
@Service
public class JobRunRecorder {

    private final JobRunPort jobRunPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public JobRunRecorder(JobRunPort jobRunPort, UuidV7 uuidV7, Clock clock) {
        this.jobRunPort = jobRunPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * Mở dòng {@code job_run} và trả về ngữ cảnh mà thân job cần.
     *
     * @param jobName     đúng tên job ở p12 §12.6, không viết tắt
     * @param triggerType scheduler, admin bấm tay, hay sự kiện
     * @param dryRun      chỉ đếm, không ghi/không xoá — <b>vẫn ghi một dòng</b> (p4 §K3: nếu
     *                    không, người vận hành không biết mình đã thử gì)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobContext start(String jobName, JobTriggerType triggerType, boolean dryRun) {
        Instant startedAt = clock.instant();
        UUID runId = uuidV7.generate();
        jobRunPort.insertStarted(runId, jobName, triggerType, dryRun, startedAt);
        return new JobContext(runId, jobName, startedAt, dryRun);
    }

    /** Đóng dòng đã mở bằng {@link #start}. Gọi đúng một lần cho mỗi {@code start}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID runId, JobOutcome outcome) {
        jobRunPort.finish(runId, outcome, clock.instant());
    }

    /**
     * {@code started_at} của lần chạy gần nhất — {@code JobHeartbeatCheckJob} dùng để phát hiện
     * job chết âm thầm (p12 §12.6.6).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Instant> lastStartedAt(String jobName) {
        return jobRunPort.findLastStartedAt(jobName);
    }
}
