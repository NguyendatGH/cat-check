package com.catcheck.media.api;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Objects;

public record ImageUpload(
        InputStream content,
        String filename,
        String contentType,
        long sizeBytes
) {

    public ImageUpload {
        Objects.requireNonNull(content, "imageUpload.content phải có giá trị");
        Objects.requireNonNull(contentType, "imageUpload.contentType phải có giá trị");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("imageUpload.sizeBytes không được âm");
        }
    }
}
