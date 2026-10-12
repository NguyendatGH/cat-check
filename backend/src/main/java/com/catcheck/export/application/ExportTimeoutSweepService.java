package com.catcheck.export.application;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.port.ExportJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExportTimeoutSweepService {

    static final String TIMEOUT_REASON =
            "Quá thời gian xử lý cho phép. Bạn thử tạo lại bản xuất nhé.";

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
