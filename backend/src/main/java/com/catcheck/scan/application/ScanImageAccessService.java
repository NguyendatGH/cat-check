package com.catcheck.scan.application;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.StorageKey;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanImage;
import com.catcheck.scan.domain.ScanImageStorageProvider;
import com.catcheck.scan.domain.port.ScanImageRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /scans/{id}/image} (p8 §8.5.4) — 404 {@code SCAN_NOT_FOUND} nếu không thuộc user
 * (KHÔNG 403, §8.3.4 ngoại lệ 3: không tiết lộ sự tồn tại), 404 {@code SCAN_IMAGE_NOT_STORED} kèm
 * {@code store_image_reason}, 410 {@code SCAN_IMAGE_EXPIRED} sau 14 ngày.
 */
@Service
public class ScanImageAccessService {

    private final ScanRepository scanRepository;
    private final ScanImageRepository scanImageRepository;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public ScanImageAccessService(ScanRepository scanRepository, ScanImageRepository scanImageRepository,
                                   ImageStorage imageStorage, Clock clock) {
        this.scanRepository = scanRepository;
        this.scanImageRepository = scanImageRepository;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    public sealed interface Result permits Stream, Redirect {
    }

    public record Stream(InputStream content, String contentType, long bytes) implements Result {
    }

    public record Redirect(URI location) implements Result {
    }

    public Result open(UUID userId, UUID scanId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        if (!scan.getUserId().equals(userId) || scan.getDeletedAt() != null) {
            throw new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND);
        }
        if (!scan.isStoreImage()) {
            throw new NotFoundException(ScanErrorCode.SCAN_IMAGE_NOT_STORED,
                    scan.getStoreImageReason() == null ? "UNKNOWN" : scan.getStoreImageReason().name());
        }
        ScanImage image = scanImageRepository.findByScanId(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_IMAGE_NOT_STORED, "UNKNOWN"));
        Instant now = clock.instant();
        if (image.isExpired(now)) {
            throw new ConflictException(ScanErrorCode.SCAN_IMAGE_EXPIRED, image.getExpiresAt());
        }

        StorageKey key = StorageKey.parse(image.getStorageKey());
        if (image.getStorageProvider() == ScanImageStorageProvider.CLOUDINARY) {
            return new Redirect(imageStorage.presignedUrl(key, Duration.ofMinutes(5)));
        }
        InputStream stream = imageStorage.open(key)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_IMAGE_NOT_STORED, "UNKNOWN"));
        return new Stream(stream, image.getContentType(), image.getBytes());
    }
}
