package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.api.CatUpdatedEvent;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.AvatarStorageProvider;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.StorageKey;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Ảnh đại diện của mèo — D9, D10, D11.
 *
 * <p><b>Module cat không tự kiểm MIME/kích thước rồi mới gọi media.</b> Nó kiểm một lần ở đây để trả
 * đúng mã mà NGƯỜI DÙNG đọc được ({@code AVATAR_INVALID} + {@code maxBytes}/{@code allowedTypes[]} theo
 * p8 §8.2.4(c)), rồi media tự kiểm lần nữa ở tầng hạ tầng. Kiểm hai lần là cố ý: media không được
 * biết giới hạn của từng tính năng, còn cat không được tin rằng mọi triển khai media đều kiểm.</p>
 *
 * <p><b>Xoá tệp nằm ngoài transaction.</b> Xoá tệp là thao tác I/O không rollback được: nếu xoá xong
 * mà transaction rollback, hồ sơ còn {@code avatar_storage_key} trỏ tới tệp không tồn tại. Nên
 * thứ tự là commit trước rồi mới xoá tệp cũ — nếu xoá hỏng thì tệp mồ côi được job retention của
 * media dọn, còn hồ sơ vẫn đúng.</p>
 */
@Service
public class CatAvatarService {

    /** p8 §8.2.4(c): ảnh đại diện tối đa 5 MB. */
    public static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    /** p6 §6.3.5 — JPEG/PNG/WebP. */
    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    /** Tách vùng lưu theo module; media không tự đoán namespace. */
    private static final String NAMESPACE = "cat-avatar";

    /**
     * TTL của URL trả về cho client.
     *
     * <p>Đủ dài cho một phiên xem ảnh, ngắn để URL không thành món đồ dùng lại lâu dài. Nếu sản phẩm
     * muốn ảnh nằm trong HTML cache dài hơn thì đổi ở đây, không phải ở controller.</p>
     */
    private static final Duration AVATAR_URL_TTL = Duration.ofHours(1);

    private final CatRepository catRepository;
    private final ImageStorage imageStorage;
    private final UserAccountPort userAccountPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CatAvatarService(
            CatRepository catRepository,
            ImageStorage imageStorage,
            UserAccountPort userAccountPort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.catRepository = catRepository;
        this.imageStorage = imageStorage;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * D9 — mở ảnh để stream ra.
     *
     * @return nội dung ảnh và kiểu MIME, hoặc {@link Optional#empty()} khi mèo chưa có ảnh
     *         ({@code 404 AVATAR_NOT_FOUND})
     */
    @Transactional(readOnly = true)
    public Optional<AvatarContent> openAvatar(UUID ownerId, UUID catId) {
        Cat cat = requireOwnedCat(ownerId, catId);
        Optional<String> key = cat.avatarKeyValue();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return imageStorage.open(new StorageKey(key.get()))
                .map(stream -> new AvatarContent(stream, "image/jpeg"));
    }

    /**
     * D10 — tải ảnh mèo.
     *
     * @return URL có hạn trả về cho client ({@code 200 {avatarUrl}})
     */
    @Transactional
    public AvatarResult replaceAvatar(UUID ownerId, UUID catId, AvatarUpload upload) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);

        String contentType = normalize(upload.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            // allowedTypes truyền xuống để client dựng được câu riêng thay vì chỉ có một thông báo chung.
            throw new BusinessRuleException(
                    CatErrorCode.AVATAR_INVALID, MAX_AVATAR_BYTES, ALLOWED_CONTENT_TYPES);
        }
        if (upload.sizeBytes() > MAX_AVATAR_BYTES) {
            throw new BusinessRuleException(CatErrorCode.PAYLOAD_TOO_LARGE, MAX_AVATAR_BYTES);
        }

        var stored = imageStorage.put(NAMESPACE, new ImageUpload(
                upload.content(), null, contentType, upload.sizeBytes()));

        Instant now = clock.instant();
        Optional<String> previousKey = cat.avatarKeyValue();
        cat.attachAvatar(stored.key().value(), AvatarStorageProvider.valueOf(imageStorage.provider()),
                now);
        catRepository.save(cat);

        // Xoá ảnh cũ SAU khi đã ghi khoá mới: xoá trước sẽ để lại hồ sơ trỏ vào tệp không tồn tại
        // nếu transaction rollback.
        previousKey.filter(key -> !key.equals(stored.key().value()))
                .ifPresent(key -> imageStorage.delete(new StorageKey(key)));

        eventPublisher.publishEvent(new CatUpdatedEvent(
                catId, ownerId, Set.of("avatar"), null, now));
        return new AvatarResult(imageStorage.presignedUrl(stored.key(), AVATAR_URL_TTL));
    }

    /**
     * D11 — gỡ ảnh.
     *
     * <p>Idempotent: gỡ ảnh hai lần trả {@code 200} chứ không {@code 404}, vì trạng thái đích
     * ("không có ảnh") đã đạt được và lỗi sẽ chỉ khiến client báo nhầm có sự cố.</p>
     */
    @Transactional
    public void removeAvatar(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
        Optional<String> previousKey = cat.avatarKeyValue();
        if (previousKey.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        cat.detachAvatar(now);
        catRepository.save(cat);
        imageStorage.delete(new StorageKey(previousKey.get()));
        eventPublisher.publishEvent(new CatUpdatedEvent(
                catId, ownerId, Set.of("avatar"), null, now));
    }

    /**
     * URL hiển thị dùng trong {@code CatResponse.avatarUrl} (D3/D4/D6/D7/D8/D10/D11) — tính CÙNG
     * một cách cho mọi endpoint trả hồ sơ mèo, tránh mỗi controller tự ký URL theo kiểu riêng.
     *
     * @return rỗng nếu mèo chưa gắn ảnh
     */
    @Transactional(readOnly = true)
    public Optional<java.net.URI> currentAvatarUrl(Cat cat) {
        return cat.avatarKeyValue().map(key -> imageStorage.presignedUrl(new StorageKey(key), AVATAR_URL_TTL));
    }

    private Cat requireOwnedCat(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    /**
     * Chuyển MIME thành dạng nhỏ, bỏ tham số {@code charset}.
     *
     * <p>Browser và thư viện HTTP gửi {@code image/jpeg} và {@code IMAGE/JPEG} theo cách khác nhau;
     * so sánh không phân biệt hoa thường giữ được hợp đồng mà không cần đổi danh sách cho phép.</p>
     */
    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parametersStart = contentType.indexOf(';');
        String base = parametersStart < 0 ? contentType : contentType.substring(0, parametersStart);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** Ảnh đã mở, sẵn sàng stream. */
    public record AvatarContent(InputStream content, String contentType) {
    }

    /** Kết quả D10. */
    public record AvatarResult(java.net.URI avatarUrl) {
    }

    /**
     * Ảnh do tầng web nhận từ multipart.
     *
     * @param sizeBytes kích thước khai báo, dùng để chặn SỚM trước khi đọc hết luồng; media vẫn kiểm
     *                  lại số byte thực tế vì client có thể khai sai
     */
    public record AvatarUpload(String contentType, InputStream content, long sizeBytes) {
    }
}
