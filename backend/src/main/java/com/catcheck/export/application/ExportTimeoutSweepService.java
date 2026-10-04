package com.catcheck.export.application;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.port.ExportJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ dọn {@code export_job} kẹt — phần có transaction của {@code ReportPdfTimeoutJob}.
 *
 * <p>Tách khỏi lớp job vì mỗi dòng phải là MỘT transaction riêng (p12 §12.6.1: "lỗi một bản ghi
 * không chặn bản ghi khác"), và vì {@code @Transactional} tự gọi trong cùng một bean sẽ bị proxy
 * bỏ qua.</p>
 */
@Service
public class ExportTimeoutSweepService {

    /** Lý do ghi vào {@code export_job.failure_reason} — hiển thị thẳng cho user (p13 §13.6.2). */
    static final String TIMEOUT_REASON =
            "Qua thoi gian xu ly cho phep. Ban thu tao lai ban xuat nhe.";

    private final ExportJobRepository jobRepository;

    public ExportTimeoutSweepService(ExportJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> findStaleIds(Instant cutoff, int limit) {
        return jobRepository.findStaleActive(cutoff, limit).stream().map(ExportJob::getId).toList();
    }

    @Transactional(readOnly = true)
    public long countStale(Instant cutoff) {
        return jobRepository.countStaleActive(cutoff);
    }

    /**
     * Đặt một job quá hạn thành {@code FAILED}.
     *
     * <p>Idempotent (p12 §12.6.1 quy tắc 3c): đọc lại dòng trong transaction riêng và chỉ ghi
     * khi nó VẪN còn {@code QUEUED}/{@code RUNNING} và VẪN quá hạn. Một worker vừa kịp hoàn
     * thành job giữa lúc quét và lúc ghi thì không bị đánh hỏng oan.</p>
     *
     * @return {@code true} nếu đã chuyển dòng sang {@code FAILED}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public boolean failIfStillStale(UUID jobId, Instant cutoff, Instant now) {
        return jobRepository.findById(jobId)
                .filter(ExportJob::isActive)
                .filter(job -> job.getRequestedAt().isBefore(cutoff))
                .map(job -> {
                    job.markFailed(TIMEOUT_REASON, now);
                    jobRepository.save(job);
                    return true;
                })
                .orElse(false);
    }
}
