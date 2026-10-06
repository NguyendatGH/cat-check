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
        return openStoredImage(scan);
    }

    /**
     * Mở ảnh <b>không</b> kiểm quyền sở hữu — dành riêng cho L6
     * ({@code GET /admin/scans/{scanId}/image}, p8 §8.4.12 mục (a)).
     *
     * <p><b>Phương thức này không tự phân quyền, và điều đó là cố ý.</b> Điều kiện của L6 không
     * phải quyền sở hữu mà là "người gọi là {@code DPO}" + "người dùng đó đang có
     * {@code dsar_request} mở" (p14 ô Q5). Cả hai được kiểm ở
     * {@link AdminScanService#openImageForDpo} — nơi có cổng {@code OpenDsarPort} và nơi ghi
     * {@code audit_log} trong cùng transaction. Gọi thẳng phương thức này từ chỗ khác là bỏ qua
     * cả hai lớp đó, nên nó {@code package-private}: chỉ {@code scan.application} thấy được.</p>
     */
    Result openForDsar(Scan scan) {
        if (scan.getDeletedAt() != null) {
            throw new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND);
        }
        return openStoredImage(scan);
    }

    private Result openStoredImage(Scan scan) {
        UUID scanId = scan.getId();
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
