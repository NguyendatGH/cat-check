package com.catcheck.media.infrastructure;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageStorage.StagedUpload;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.ImageVariant;
import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.media.api.StorageKey;
import com.catcheck.media.api.StoredImage;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Triển khai {@link ImageStorage} trên tệp cục bộ — mặc định của môi trường VPS một tiến trình
 * (p18: chưa cần Cloudinary cho Phase 1).
 *
 * <p>Đảm bảo bốn điều mà p4/p6 yêu cầu:</p>
 * <ul>
 *   <li><b>Không lộ đường dẫn tuyệt đối.</b> {@link #presignedUrl} trả URL qua endpoint nội bộ
 *       có HMAC + mốc hạn, không phải {@code file://} hay đường dẫn đĩa (p8 §8.1.3, p6 §6.11.2).</li>
 *   <li><b>Ghi nguyên tử.</b> Mọi lần ghi đi qua tệp tạm rồi {@code ATOMIC_MOVE}, nên tiến trình
 *       chết giữa chừng không để lại tệp nửa vời.</li>
 *   <li><b>Chặn trèo thư mục.</b> {@link StorageKey} đã chặn {@code ..}; ở đây kiểm tra lần hai
 *       bằng {@code normalize().startsWith(root)} để không phụ thuộc hoàn toàn vào tầng trên.</li>
 *   <li><b>Bám mốc thời gian từ {@link Clock}.</b> Không gọi {@code Instant.now()} trực tiếp (R13).</li>
 * </ul>
 */
@Component
public class LocalImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);

    private static final String PROVIDER = "LOCAL";
    private static final String STAGING_NAMESPACE = "staging";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int COPY_BUFFER_BYTES = 8192;

    private final MediaProperties properties;
    private final Clock clock;
    private final UuidV7 uuidV7;

    public LocalImageStorage(MediaProperties properties, Clock clock, UuidV7 uuidV7) {
        this.properties = properties;
        this.clock = clock;
        this.uuidV7 = uuidV7;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public StoredImage put(String namespace, ImageUpload upload) {
        Instant now = clock.instant();
        StorageKey key = new StorageKey(namespace + "/" + datePath(now) + "/" + uuidV7.generate()
                + extensionFor(upload.contentType()));
        return writeAtomically(key, upload);
    }

    @Override
    public StagedUpload stage(String namespace, ImageUpload upload, java.time.Duration ttl) {
        StorageKey stagedKey = new StorageKey(
                STAGING_NAMESPACE + "/" + namespace + "/" + uuidV7.generate()
                        + extensionFor(upload.contentType()));
        StoredImage written = writeAtomically(stagedKey, upload);
        java.time.Duration effectiveTtl = ttl == null ? properties.stagingTtl() : ttl;
        return new StagedUpload(stagedKey, written.contentType(), written.sizeBytes(),
                clock.instant().plus(effectiveTtl));
    }

    @Override
    public StoredImage commit(StagedUpload staged) {
        Path source = resolve(staged.stagedKey());
        if (!Files.isRegularFile(source)) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_KEY_NOT_FOUND, staged.stagedKey().value());
        }
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String extension = dot == -1 ? "" : fileName.substring(dot);
        StorageKey key = new StorageKey(stagingNamespaceOf(staged.stagedKey()) + "/" + datePath(clock.instant())
                + "/" + fileName.substring(0, dot == -1 ? fileName.length() : dot) + extension);
        move(source, resolve(key));
        return new StoredImage(key, PROVIDER, staged.sizeBytes(), staged.contentType());
    }

    @Override
    public StoredImage putVariant(StorageKey parent, ImageVariant variant, ImageUpload upload) {
        return writeAtomically(parent.parentKey(variant), upload);
    }

    @Override
    public Optional<InputStream> open(StorageKey key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("Không mở được tệp đã lưu: " + key.value(), ex);
        }
    }

    /**
     * Mở một tệp được tham chiếu bởi URL HMAC do {@link #presignedUrl} phát ra.
     * Endpoint media dùng phương thức này thay vì mở tệp trực tiếp để chữ ký và hạn URL
     * luôn được kiểm tra ở cùng một nơi với lúc ký.
     */
    public Optional<StoredMedia> openSigned(StorageKey key, long expiresEpochSecond, String signature) {
        if (signature == null || signature.isBlank()
                || expiresEpochSecond < clock.instant().getEpochSecond()) {
            return Optional.empty();
        }
        String expected = sign(key.value(), expiresEpochSecond);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return open(key).map(content -> new StoredMedia(content, contentTypeFor(key)));
    }

    @Override
    public URI presignedUrl(StorageKey key, java.time.Duration ttl) {
        if (properties.urlSigningKey() == null || properties.urlSigningKey().isBlank()) {
            // KHÔNG phát URL không ký: URL không chữ ký chính là đường dẫn đoán được, tức đường dẫn
            // tuyệt đối đã bị lộ qua bước dò. Bắt buộc cấu hình khoá ở môi trường thật.
            throw new BusinessRuleException(MediaErrorCode.STORAGE_IO_ERROR,
                    "catcheck.storage.local.url-signing-key chưa được cấu hình");
        }
        long expiresEpochSecond = clock.instant().plus(ttl).getEpochSecond();
        String signature = sign(key.value(), expiresEpochSecond);
        return URI.create("%s/api/v1/media/%s?expires=%d&sig=%s".formatted(
                properties.publicBaseUrl(),
                URLEncoder.encode(key.value(), StandardCharsets.UTF_8),
                expiresEpochSecond,
                signature));
    }

    @Override
    public boolean exists(StorageKey key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(StorageKey key) {
        deleteQuietly(resolve(key));
        deleteVariants(key);
    }

    @Override
    public void deleteVariants(StorageKey parent) {
        Path parentPath = resolve(parent);
        Path directory = parentPath.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        String prefix = stripExtension(parent.value()).substring(parent.value().lastIndexOf('/') + 1) + "-";
        try (Stream<Path> siblings = Files.list(directory)) {
            siblings.filter(candidate -> isKnownVariantOf(candidate.getFileName().toString(), prefix))
                    .forEach(this::deleteQuietly);
        } catch (IOException ex) {
            log.warn("Không liệt kê được biến thể phái sinh cạnh {}", parent.value());
        }
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        // CHỈ quét vùng staging. Tệp vĩnh viễn không bị đụng tới: vòng đời xoá thuộc module sở hữu
        // dữ liệu (p15 retention), vì ảnh quét, avatar mèo và tệp export có hạn khác nhau (p7 §7.2.3).
        deleteStagedOlderThan(cutoff);
    }

    @Override
    public int deleteExpiredStaging(Instant now) {
        return deleteStagedOlderThan(now.minus(properties.stagingTtl()));
    }

    @Override
    public Stream<StorageKey> list(String namespace) {
        Path namespaceRoot = resolve(new StorageKey(namespace));
        if (!Files.isDirectory(namespaceRoot)) {
            return Stream.empty();
        }
        List<StorageKey> keys;
        try (Stream<Path> files = Files.walk(namespaceRoot)) {
            keys = files.filter(Files::isRegularFile)
                    .map(file -> toStorageKey(file, properties.root()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (IOException ex) {
            log.warn("Không liệt kê được namespace {}: {}", namespace, ex.getMessage());
            return Stream.empty();
        }
        return keys.stream();
    }

    // ---------------------------------------------------------------- internals

    private int deleteStagedOlderThan(Instant cutoff) {
        Path stagingRoot = resolve(new StorageKey(STAGING_NAMESPACE));
        if (!Files.isDirectory(stagingRoot)) {
            return 0;
        }
        int[] deleted = {0};
        try (Stream<Path> staged = Files.walk(stagingRoot)) {
            staged.filter(Files::isRegularFile)
                    .filter(path -> modifiedBefore(path, cutoff))
                    .forEach(path -> {
                        deleteQuietly(path);
                        deleted[0]++;
                    });
        } catch (IOException ex) {
            log.warn("Không dọn được thư mục staging: {}", ex.getMessage());
        }
        return deleted[0];
    }

    private StoredImage writeAtomically(StorageKey key, ImageUpload upload) {
        Path target = resolve(key);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".part");
            long written = copyBounded(upload.content(), temporary);
            move(temporary, target);
            return new StoredImage(key, PROVIDER, written, upload.contentType());
        } catch (IOException ex) {
            if (temporary != null) {
                deleteQuietly(temporary);
            }
            throw new UncheckedIOException("Không ghi được ảnh vào storage: " + key.value(), ex);
        }
    }

    /**
     * Chép có kiểm tra trần kích thước trên SỐ BYTE THỰC TẾ, không tin {@code sizeBytes} do client
     * khai: client nói 1 KB rồi gửi 50 MB vẫn phải bị chặn.
     */
    private long copyBounded(InputStream source, Path target) throws IOException {
        long maxBytes = properties.maxImageBytes();
        long total = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new BusinessRuleException(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE, maxBytes, total);
                }
                out.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, "image/*");
        }
        return total;
    }

    private void move(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Không di chuyển được tệp trong storage", ex);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Không xoá được tệp trong storage: {}", ex.getMessage());
        }
    }

    private boolean modifiedBefore(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException ex) {
            return false;
        }
    }

    private Path resolve(StorageKey key) {
        Path root = properties.root().toAbsolutePath().normalize();
        Path resolved = root.resolve(key.value()).normalize();
        if (!resolved.startsWith(root)) {
            // Lớp phòng thủ thứ hai, không phụ thuộc vào việc mọi lời gọi đều đi qua StorageKey.
            throw new BusinessRuleException(MediaErrorCode.STORAGE_KEY_NOT_FOUND, key.value());
        }
        return resolved;
    }

    private String datePath(Instant now) {
        LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
        return DateTimeFormatter.ofPattern("yyyy/MM/dd").format(date);
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, contentType);
        };
    }

    private String contentTypeFor(StorageKey key) {
        String value = key.value().toLowerCase(java.util.Locale.ROOT);
        if (value.endsWith(".png")) {
            return "image/png";
        }
        if (value.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String sign(String keyValue, long expiresEpochSecond) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.urlSigningKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(
                    (keyValue + "\n" + expiresEpochSecond).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_IO_ERROR, "Không ký được URL có hạn");
        }
    }

    private boolean isKnownVariantOf(String fileName, String prefix) {
        if (!fileName.startsWith(prefix)) {
            return false;
        }
        String remainder = fileName.substring(prefix.length());
        int dot = remainder.indexOf('.');
        String suffix = dot == -1 ? remainder : remainder.substring(0, dot);
        for (ImageVariant variant : ImageVariant.values()) {
            if (variant.suffix().equals(suffix)) {
                return true;
            }
        }
        return false;
    }

    private String stagingNamespaceOf(StorageKey stagedKey) {
        String[] segments = stagedKey.value().split("/");
        return segments.length >= 3 ? segments[2] : STAGING_NAMESPACE;
    }

    private String stripExtension(String keyValue) {
        int slash = keyValue.lastIndexOf('/');
        int dot = keyValue.lastIndexOf('.');
        return dot > slash ? keyValue.substring(0, dot) : keyValue;
    }

    private static StorageKey toStorageKey(Path file, Path root) {
        String rootPath = root.toAbsolutePath().normalize().toString().replace('\\', '/');
        String filePath = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (!filePath.startsWith(rootPath)) {
            return null;
        }
        String relative = filePath.substring(rootPath.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        try {
            return new StorageKey(relative);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Nội dung và MIME type của một tệp local đã xác thực URL. */
    public record StoredMedia(InputStream content, String contentType) {
    }
}
