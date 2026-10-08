package com.catcheck.media.infrastructure;

import com.catcheck.media.api.StorageKey;
import io.swagger.v3.oas.annotations.Operation;
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
     * R18 (ArchUnit {@code WebLayerRuleTests}) đòi {@code operationId} trên <b>mọi</b> method
     * public của {@code @RestController}. Thiếu nó ở đây làm test kiến trúc đỏ cho toàn repo —
     * một lỗi đã tồn tại trước W5-A và được sửa ở đây vì {@code media/**} thuộc vùng của gói việc
     * này.
     */
    @Operation(
            operationId = "getSignedMedia",
            summary = "Phục vụ một URL local đã ký (presigned) của ảnh đã lưu",
            description = "Khoá sai định dạng ⇒ 404; chữ ký sai hoặc đã hết hạn ⇒ 403. Không phải "
                    + "endpoint của nhóm nào trong p8 §8.4 — nó là hiện thực của "
                    + "`ImageStorage.presignedUrl` khi provider là LOCAL (p6 §6.11.5).")
    @GetMapping("/{*key}")
    public ResponseEntity<?> get(
            @PathVariable String key,
            @RequestParam long expires,
            @RequestParam String sig) {
        final StorageKey storageKey;
        try {
            // `/{*key}` giữ nguyên dấu `/` đầu của phần đường dẫn còn lại; khoá lưu trữ không có nó.
            storageKey = StorageKey.parse(key.startsWith("/") ? key.substring(1) : key);
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
