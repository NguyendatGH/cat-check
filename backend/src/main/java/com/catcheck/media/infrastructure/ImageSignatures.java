package com.catcheck.media.infrastructure;

import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.shared.error.BusinessRuleException;

/**
 * Kiểm MIME bằng magic bytes trước khi ghi — {@code Content-Type} do client khai (avatar mèo,
 * avatar người dùng) không đủ tin: khai {@code image/png} rồi gửi HTML/tệp tuỳ ý vẫn phải bị chặn.
 * Cùng quy tắc nhận diện với {@code scan.infrastructure.vision.OpenCvRawImageProcessor}.
 */
final class ImageSignatures {

    /** Số byte đầu cần đọc để nhận diện cả ba định dạng (WebP cần tới byte 11). */
    static final int HEAD_BYTES = 12;

    private ImageSignatures() {
    }

    /** Ném {@link MediaErrorCode#STORAGE_UNSUPPORTED_TYPE} nếu nội dung không đúng định dạng đã khai. */
    static void requireMatches(String declaredContentType, byte[] head) {
        String actual = sniff(head);
        if (actual == null || !actual.equals(declaredContentType)) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, declaredContentType);
        }
    }

    private static String sniff(byte[] b) {
        if (b == null || b.length < HEAD_BYTES) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        boolean riff = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F';
        boolean webp = b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return riff && webp ? "image/webp" : null;
    }
}
