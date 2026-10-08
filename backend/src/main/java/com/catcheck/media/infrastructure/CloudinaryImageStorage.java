package com.catcheck.media.infrastructure;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.ImageVariant;
import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.media.api.StorageKey;
import com.catcheck.media.api.StoredImage;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * {@link ImageStorage} trên Cloudinary, gọi REST bằng {@link RestClient} (không thêm SDK).
 *
 * <p>Ảnh được tải lên với {@code type=authenticated} nên KHÔNG công khai; chỉ URL giao hàng có
 * chữ ký mới xem được. {@code public_id = {folder}/{khoá không đuôi}}; đuôi file của khoá dùng để
 * dựng URL giao hàng.</p>
 *
 * <p><b>TTL bị bỏ qua</b>: URL ký kiểu {@code s--sig--} của Cloudinary không hết hạn (chữ ký chỉ
 * ràng buộc đường dẫn). {@link #presignedUrl} vì vậy trả URL ổn định; coi nó là URL riêng tư,
 * không công khai đại trà. Bí mật và URL ký không bao giờ được đưa vào message/log.</p>
 */
public class CloudinaryImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);

    static final String PROVIDER = "CLOUDINARY";
    private static final String TYPE = "authenticated";
    private static final String STAGING_NAMESPACE = "staging";
    private static final String API_BASE = "https://api.cloudinary.com/v1_1/";
    private static final String DELIVERY_BASE = "https://res.cloudinary.com/";
    private static final int LIST_PAGE = 500;
    private static final int LIST_MAX_PAGES = 20;

    private final StorageProperties.Cloudinary config;
    private final MediaProperties media;
    private final Clock clock;
    private final UuidV7 uuidV7;
    private final RestClient client;

    public CloudinaryImageStorage(StorageProperties.Cloudinary config, MediaProperties media,
                                  Clock clock, UuidV7 uuidV7) {
        this.config = config;
        this.media = media;
        this.clock = clock;
        this.uuidV7 = uuidV7;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    // ------------------------------------------------------------------ write

    @Override
    public StoredImage put(String namespace, ImageUpload upload) {
        StorageKey key = new StorageKey(namespace + "/" + datePath() + "/" + uuidV7.generate()
                + extensionFor(upload.contentType()));
        return upload(key, upload);
    }

    @Override
    public StagedUpload stage(String namespace, ImageUpload upload, Duration ttl) {
        StorageKey key = new StorageKey(STAGING_NAMESPACE + "/" + namespace + "/" + uuidV7.generate()
                + extensionFor(upload.contentType()));
        StoredImage written = upload(key, upload);
        Duration effective = ttl == null ? media.stagingTtl() : ttl;
        return new StagedUpload(key, written.contentType(), written.sizeBytes(), clock.instant().plus(effective));
    }

    @Override
    public StoredImage commit(StagedUpload staged) {
        String stagedValue = staged.stagedKey().value();
        String[] segments = stagedValue.split("/");
        String namespace = segments.length >= 3 ? segments[1] : STAGING_NAMESPACE;
        String fileName = segments[segments.length - 1];
        StorageKey target = new StorageKey(namespace + "/" + datePath() + "/" + fileName);
        Map<String, String> params = new TreeMap<>();
        params.put("from_public_id", publicId(staged.stagedKey()));
        params.put("to_public_id", publicId(target));
        params.put("type", TYPE);
        params.put("to_type", TYPE);
        postSigned("image/rename", params, true);
        return new StoredImage(target, PROVIDER, staged.sizeBytes(), staged.contentType());
    }

    @Override
    public StoredImage putVariant(StorageKey parent, ImageVariant variant, ImageUpload upload) {
        return upload(parent.parentKey(variant), upload);
    }

    private StoredImage upload(StorageKey key, ImageUpload upload) {
        byte[] bytes = readBounded(upload);
        Map<String, String> params = new TreeMap<>();
        params.put("public_id", publicId(key));
        params.put("timestamp", Long.toString(clock.instant().getEpochSecond()));
        params.put("type", TYPE);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        String fileName = key.value().substring(key.value().lastIndexOf('/') + 1);
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        params.forEach(form::add);
        form.add("api_key", config.apiKey());
        form.add("signature", sign(params, config.apiSecret()));
        try {
            client.post().uri(apiUrl("image/upload"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            throw ioError("upload", ex);
        }
        return new StoredImage(key, PROVIDER, bytes.length, upload.contentType());
    }

    private byte[] readBounded(ImageUpload upload) {
        long max = media.maxImageBytes();
        try {
            byte[] bytes = upload.content().readNBytes((int) Math.min(max + 1, Integer.MAX_VALUE - 8));
            if (bytes.length > max) {
                throw new BusinessRuleException(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE, max, bytes.length);
            }
            if (bytes.length == 0) {
                throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, "image/*");
            }
            return bytes;
        } catch (IOException ex) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_IO_ERROR, "Không đọc được ảnh tải lên");
        }
    }

    // ------------------------------------------------------------------- read

    @Override
    public Optional<InputStream> open(StorageKey key) {
        try {
            byte[] body = client.get().uri(deliveryUrl(key)).retrieve().body(byte[].class);
            return body == null || body.length == 0 ? Optional.empty() : Optional.of(new ByteArrayInputStream(body));
        } catch (RestClientResponseException ex) {
            if (isNotFound(ex.getStatusCode())) {
                return Optional.empty();
            }
            throw ioError("open", ex);
        } catch (RuntimeException ex) {
            throw ioError("open", ex);
        }
    }

    /** TTL bị bỏ qua: xem Javadoc lớp. */
    @Override
    public URI presignedUrl(StorageKey key, Duration ttl) {
        return deliveryUrl(key);
    }

    @Override
    public boolean exists(StorageKey key) {
        try {
            client.head().uri(deliveryUrl(key)).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (isNotFound(ex.getStatusCode())) {
                return false;
            }
            throw ioError("exists", ex);
        } catch (RuntimeException ex) {
            throw ioError("exists", ex);
        }
    }

    // ----------------------------------------------------------------- delete

    @Override
    public void delete(StorageKey key) {
        destroy(key);
        deleteVariants(key);
    }

    @Override
    public void deleteVariants(StorageKey parent) {
        for (ImageVariant variant : ImageVariant.values()) {
            destroy(parent.parentKey(variant));
        }
    }

    private void destroy(StorageKey key) {
        Map<String, String> params = new TreeMap<>();
        params.put("public_id", publicId(key));
        params.put("type", TYPE);
        params.put("invalidate", "true");
        postSigned("image/destroy", params, false);
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        deleteStagedOlderThan(cutoff);
    }

    @Override
    public int deleteExpiredStaging(Instant now) {
        return deleteStagedOlderThan(now.minus(media.stagingTtl()));
    }

    private int deleteStagedOlderThan(Instant cutoff) {
        int deleted = 0;
        for (Resource resource : listResources(STAGING_NAMESPACE)) {
            if (resource.createdAt() != null && resource.createdAt().isBefore(cutoff)) {
                destroy(resource.key());
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public Stream<StorageKey> list(String namespace) {
        return listResources(namespace).stream().map(Resource::key);
    }

    private record Resource(StorageKey key, Instant createdAt) {
    }

    @SuppressWarnings("unchecked")
    private List<Resource> listResources(String namespace) {
        List<Resource> result = new ArrayList<>();
        String prefix = folderPrefix() + namespace + "/";
        String cursor = null;
        String basic = Base64.getEncoder().encodeToString(
                (config.apiKey() + ":" + config.apiSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            for (int page = 0; page < LIST_MAX_PAGES; page++) {
                final String currentCursor = cursor;
                Map<String, Object> body = client.get()
                        .uri(b -> {
                            var u = b.scheme("https").host("api.cloudinary.com")
                                    .path("/v1_1/{cloud}/resources/image/authenticated")
                                    .queryParam("prefix", prefix)
                                    .queryParam("max_results", LIST_PAGE);
                            if (currentCursor != null) {
                                u.queryParam("next_cursor", currentCursor);
                            }
                            return u.build(config.cloudName());
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() { });
                if (body == null) {
                    break;
                }
                Object resources = body.get("resources");
                if (resources instanceof List<?> list) {
                    for (Object item : list) {
                        Map<String, Object> r = (Map<String, Object>) item;
                        String publicId = String.valueOf(r.get("public_id"));
                        String format = r.get("format") == null ? "" : "." + r.get("format");
                        String keyValue = publicId.startsWith(folderPrefix())
                                ? publicId.substring(folderPrefix().length()) : publicId;
                        try {
                            Instant created = r.get("created_at") == null ? null
                                    : Instant.parse(String.valueOf(r.get("created_at")));
                            result.add(new Resource(new StorageKey(keyValue + format), created));
                        } catch (RuntimeException ignored) {
                            // bỏ qua tài nguyên có tên không hợp lệ với StorageKey
                        }
                    }
                }
                Object next = body.get("next_cursor");
                if (next == null) {
                    break;
                }
                cursor = String.valueOf(next);
            }
        } catch (RuntimeException ex) {
            throw ioError("list", ex);
        }
        return result;
    }

    // --------------------------------------------------------------- internals

    private void postSigned(String path, Map<String, String> signedParams, boolean failOnError) {
        Map<String, String> params = new TreeMap<>(signedParams);
        params.put("timestamp", Long.toString(clock.instant().getEpochSecond()));
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        form.add("api_key", config.apiKey());
        form.add("signature", sign(params, config.apiSecret()));
        try {
            client.post().uri(apiUrl(path))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            // destroy/rename trên khoá không còn: "không ném lỗi nếu khoá đã biến mất"
            if (isNotFound(ex.getStatusCode()) && !path.endsWith("rename")) {
                return;
            }
            throw ioError(path, ex);
        } catch (RuntimeException ex) {
            throw ioError(path, ex);
        }
    }

    private URI apiUrl(String path) {
        return URI.create(API_BASE + config.cloudName() + "/" + path);
    }

    private static boolean isNotFound(HttpStatusCode status) {
        return status.value() == 404;
    }

    private BusinessRuleException ioError(String operation, RuntimeException ex) {
        // Không đưa message của ex vào log/lỗi: có thể chứa URL ký.
        log.warn("Cloudinary {} thất bại ({})", operation, ex.getClass().getSimpleName());
        return new BusinessRuleException(MediaErrorCode.STORAGE_IO_ERROR, "Lưu trữ ảnh lỗi: " + operation);
    }

    private String folderPrefix() {
        String folder = config.folder();
        return folder == null || folder.isBlank() ? "" : folder.replaceAll("^/+|/+$", "") + "/";
    }

    /** {@code {folder}/{khoá không đuôi}}. */
    String publicId(StorageKey key) {
        return folderPrefix() + stripExtension(key.value());
    }

    URI deliveryUrl(StorageKey key) {
        return deliveryUrl(config.cloudName(), config.apiSecret(), publicId(key), extensionOf(key.value()));
    }

    /** URL giao hàng ký của type authenticated. {@code ext} có thể rỗng (biến thể không đuôi). */
    static URI deliveryUrl(String cloudName, String apiSecret, String publicId, String ext) {
        String path = ext.isEmpty() ? publicId : publicId + "." + ext;
        String signature = deliverySignature(path, apiSecret);
        return URI.create(DELIVERY_BASE + cloudName + "/image/" + TYPE + "/s--" + signature + "--/" + path);
    }

    /** 8 ký tự đầu của base64url(SHA-1(toSign + secret)). */
    static String deliverySignature(String toSign, String apiSecret) {
        byte[] digest = sha1((toSign + apiSecret).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 8);
    }

    /** Chữ ký API: k=v sắp theo tên, nối '&', cộng secret, SHA-1 hex. */
    static String sign(Map<String, String> params, String apiSecret) {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(k).append('=').append(v);
        });
        sb.append(apiSecret);
        return HexFormat.of().formatHex(sha1(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 không khả dụng", ex);
        }
    }

    private static String stripExtension(String value) {
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        return dot > slash ? value.substring(0, dot) : value;
    }

    private static String extensionOf(String value) {
        int slash = value.lastIndexOf('/');
        int dot = value.lastIndexOf('.');
        return dot > slash ? value.substring(dot + 1) : "";
    }

    private String datePath() {
        return DateTimeFormatter.ofPattern("yyyy/MM/dd")
                .format(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, contentType);
        };
    }
}
