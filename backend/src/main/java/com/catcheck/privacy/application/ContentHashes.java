package com.catcheck.privacy.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 của nội dung bằng chứng (p15 §15.10-C): {@code policy_version.content_hash}
 * là SHA-256 của {@code content_md}; {@code consent_record.policy_hash} là snapshot của
 * con số đó lúc user đồng ý; {@code consent_record.consent_text_hash} là hash của
 * ĐÚNG chuỗi text cạnh checkbox lúc đó.
 *
 * <p>Đây là "cốt lõi của nghĩa vụ chứng minh" (Điều 6.2 NĐ356): phải chứng minh được
 * user đã đồng ý với <b>chính xác nội dung nào</b>, không phải với "Chính sách quyền
 * riêng tư" nói chung.</p>
 */
public final class ContentHashes {

    private ContentHashes() {
    }

    /** SHA-256 dạng hex thường, đúng 64 ký tự — khớp cột {@code CHAR(64)}. */
    public static String sha256Hex(String content) {
        if (content == null) {
            throw new IllegalArgumentException("Không hash được nội dung null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM thiếu SHA-256 — không thể ghi bằng chứng consent", ex);
        }
    }
}
