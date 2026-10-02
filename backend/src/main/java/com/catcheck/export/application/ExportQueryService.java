package com.catcheck.export.application;

import com.catcheck.export.api.ExportErrorCode;
import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.ExportStatus;
import com.catcheck.export.domain.port.ExportJobRepository;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.StorageKey;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Clock;
import java.util.UUID;

/** {@code GET /exports}, {@code GET /exports/{id}}, {@code GET /exports/{id}/download} (p8 §8.4.10 J2-J4). */
@Service
public class ExportQueryService {

    private final ExportJobRepository jobRepository;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public ExportQueryService(ExportJobRepository jobRepository, ImageStorage imageStorage, Clock clock) {
        this.jobRepository = jobRepository;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    public ExportJobRepository.Page list(UUID userId, String cursor, int limit) {
        return jobRepository.findByUser(userId, cursor, Math.min(Math.max(limit, 1), 100));
    }

    public ExportJob detail(UUID userId, UUID jobId) {
        return requireOwned(userId, jobId);
    }

    public record Download(InputStream content, long bytes, String filename) {
    }

    public Download download(UUID userId, UUID jobId) {
        ExportJob job = requireOwned(userId, jobId);
        if (job.getStatus() == ExportStatus.EXPIRED) {
            throw new ConflictException(ExportErrorCode.EXPORT_EXPIRED);
        }
        if (job.getStatus() != ExportStatus.READY) {
            throw new ConflictException(ExportErrorCode.EXPORT_NOT_READY);
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(clock.instant())) {
            job.markExpired();
            jobRepository.save(job);
            throw new ConflictException(ExportErrorCode.EXPORT_EXPIRED);
        }
        InputStream stream = imageStorage.open(StorageKey.parse(job.getFileRef()))
                .orElseThrow(() -> new ConflictException(ExportErrorCode.EXPORT_EXPIRED));
        job.recordDownload(clock.instant());
        jobRepository.save(job);
        return new Download(stream, job.getFileBytes() == null ? 0 : job.getFileBytes(),
                job.getDocumentCode() + ".pdf");
    }

    private ExportJob requireOwned(UUID userId, UUID jobId) {
        ExportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException(ExportErrorCode.EXPORT_JOB_NOT_FOUND));
        if (!job.getUserId().equals(userId)) {
            throw new NotFoundException(ExportErrorCode.EXPORT_JOB_NOT_FOUND);
        }
        return job;
    }
}
