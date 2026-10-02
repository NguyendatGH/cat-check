package com.catcheck.media.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Khoá lưu trữ tệp nhị phân — chuỗi vị trí tương đối, KHÔNG phải đường dẫn tuyệt đối và KHÔNG
 * phải URL (p8 §8.1.3: "Không lộ ... {@code storage_key}, đường dẫn file tuyệt đối").
 *
 * <p>Đây là kiểu DUY NHẤT được đưa vào chữ ký của {@link ImageStorage}. Vì mọi tham số và kiểu
 * trả về của cổng lưu trữ đều nằm trong package {@code media.api} (named interface "api"), các
 * module khác dùng cổng này mà không phải chạm bất kỳ package nào khác của module media — tuân
 * thủ p7 §7.3: kiểu đi qua biên module phải nằm trong {@code <module>.api}.</p>
 *
 * <p>Định dạng: {@code <namespace>/<yyyy>/<MM>/<dd>/<uuidv7>.<ext>} — ví dụ
 * {@code cat-avatar/2026/09/27/01997c3a-8f21-7c4e-b2a1-9f0d3e5b7a62.jpg}. Phân tầng theo ngày
 * giữ mỗi thư mục nhỏ, tránh vài triệu file trong một thư mục (p11 §11.9).</p>
 *
 * <p>Chỉ cho phép {@code [a-z0-9/_-]} và bắt buộc có dấu chấm trong {@code namespace}, để một
 * khoá do module khác dựng ra không thể trèo ra khỏi thư mục gốc ({@code ..}) hay ghi đè
 * khoá của module khác.</p>
 */
public record StorageKey(String value) {

    /**
     * Bug that da sua: pattern cu {@code [a-z0-9-]+} KHONG cho dau cham, trong khi
     * {@code LocalImageStorage#put} dung sinh khoa dang
     * {@code cat-avatar/2026/09/28/<uuid>.png} — moi lan upload anh (avatar meo, avatar user,
     * anh scan) deu nem {@code IllegalArgumentException} ⇒ 500. Javadoc ngay tren lop nay ghi
     * {@code [a-z0-9/_-]} va vi du {@code a1b2.jpg}, tuc dau cham von la co y.
     *
     * <p>Cho them {@code .} va {@code _} KHONG lam yeu chong path traversal: {@code ..},
     * {@code \}, {@code /} dau/cuoi va {@code //} van bi chan boi cac kiem tra rieng ben duoi.</p>
     */
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

    /** Khoá cha của một biến thể phái sinh, ví dụ {@code cat-avatar/.../a1b2.jpg} → {@code cat-avatar/.../a1b2}. */
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
