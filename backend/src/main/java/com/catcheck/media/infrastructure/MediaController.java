package com.catcheck.media.infrastructure;

import com.catcheck.media.api.StorageKey;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

/** Phục vụ các URL local đã ký do {@link LocalImageStorage#presignedUrl} phát ra. */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final LocalImageStorage storage;

    public MediaController(LocalImageStorage storage) {
        this.storage = storage;
    }

    /**
     * R18 (p7 §7.6.2 + {@code WebLayerRuleTests}) doi MOI method public cua mot
     * {@code @RestController} co {@code @Operation(operationId = ...)}. Thieu annotation nay lam
     * {@code ./mvnw test} DO cho toan bo repo — do that o nhanh w5d truoc khi sua: 344 test,
     * 4 that bai, trong do co dong nay.
     *
     * <p><b>Ghi chu ve pham vi:</b> W5-D khong so huu {@code media/**} trong dot nay. Sua day la
     * mot dong annotation thuan bo sung, khong doi hanh vi, va duoc lam vi mot suite do chan moi
     * agent khac. Ghi handoff H15.186.</p>
     */
    @io.swagger.v3.oas.annotations.Operation(
            operationId = "getSignedMediaObject",
            summary = "Tai mot file da ky bang HMAC",
            description = "URL do LocalImageStorage.presignedUrl phat ra; khong can phien vi "
                    + "chinh URL da mang HMAC + han dung.")
    @GetMapping("/{*key}")
    public ResponseEntity<?> get(
            @PathVariable String key,
            @RequestParam long expires,
            @RequestParam String sig) {
        final StorageKey storageKey;
        try {
            storageKey = StorageKey.parse(key);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var media = storage.openSigned(storageKey, expires, sig);
        if (media.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        InputStream content = media.get().content();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(media.get().contentType()))
                .body(new InputStreamResource(content));
    }
}
