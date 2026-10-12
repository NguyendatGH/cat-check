package com.catcheck.media.api;

import java.util.Objects;
import java.util.regex.Pattern;

public record StorageKey(String value) {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z0-9._-]+");
    private static final int MAX_LENGTH = 255;

    public StorageKey {
        Objects.requireNonNull(value, "storageKey.value phải có giá trị");
        validate(value);
    }

    private static void validate(String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("storageKey không được rỗng");
        }
        if (key.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("storageKey dài tối đa " + MAX_LENGTH + " ký tự");
        }
        if (key.startsWith("/") || key.endsWith("/") || key.contains("//")) {
            throw new IllegalArgumentException("storageKey phải là đường dẫn tương đối: " + key);
        }
        if (key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("storageKey không được chứa '..' hoặc dấu gạch ngược: " + key);
        }
        String[] segments = key.split("/");
        for (String segment : segments) {
            if (!SAFE_SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException(
                        "storageKey chỉ cho phép [a-z0-9._-] trong mỗi phần tử: " + key);
            }
        }
    }

    public StorageKey parentKey(ImageVariant variant) {
        int dot = value.lastIndexOf('.');
        String stem = dot == -1 ? value : value.substring(0, dot);
        return new StorageKey(stem + "-" + variant.suffix());
    }

    public static StorageKey parse(String value) {
        return new StorageKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
