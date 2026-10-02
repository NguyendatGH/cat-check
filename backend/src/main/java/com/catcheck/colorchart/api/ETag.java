package com.catcheck.colorchart.api;

import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.shared.error.BusinessRuleException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * ETag &amp; If-Match theo p8 §8.1.11.
 *
 * <p>Định dạng: {@code W/"<updatedAt epoch ms>-<id 8 ký tự>"}. Bắt buộc {@code If-Match} với mọi
 * endpoint ghi cấu hình admin ảnh hưởng toàn hệ thống — thiếu ⇒ {@code 428 PRECONDITION_REQUIRED},
 * không khớp ⇒ {@code 412 RESOURCE_MODIFIED}.
 *
 * <p>Dùng {@code W/} (weak validator) vì {@code updatedAt} có độ chính xác millisecond — hai lần
 * ghi trong cùng một millisecond cho cùng một giá trị ETag, điều mà strong validator sẽ coi là
 * khác nhau oan.
 */
final class ETag {

    private ETag() {
    }

    /** ETag cho một bảng màu. */
    static String of(ColorChart chart) {
        return weak(chart.getUpdatedAt(), chart.getId().toString());
    }

    /** ETag cho một dải phân loại. */
    static String of(PhClassificationBand band) {
        return weak(band.getUpdatedAt(), band.getId().toString());
    }

    /** ETag cho danh sách dải — băm tổng hợp để thay đổi bất kỳ dải nào cũng đổi ETag. */
    static String ofBands(List<PhClassificationBand> bands) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (PhClassificationBand band : bands) {
                digest.update(band.getId().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(band.getUpdatedAt().toString().getBytes(StandardCharsets.UTF_8));
            }
            return "W/\"bands-" + HexFormat.of().formatHex(digest.digest()).substring(0, 12) + "\"";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    /**
     * Kiểm tra {@code If-Match} của request với ETag hiện tại.
     *
     * @throws BusinessRuleException {@code 428} nếu thiếu header, {@code 412} nếu không khớp
     */
    static void requireMatch(String ifMatch, Object current) {
        String expected = switch (current) {
            case ColorChart chart -> of(chart);
            case PhClassificationBand band -> of(band);
            default -> throw new IllegalArgumentException("Không hỗ trợ ETag cho " + current.getClass());
        };
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessRuleException(ColorChartErrorCode.PRECONDITION_REQUIRED);
        }
        String normalized = ifMatch.trim();
        if (!normalized.equals(expected) && !normalized.equals("*")) {
            throw new BusinessRuleException(ColorChartErrorCode.RESOURCE_MODIFIED, expected);
        }
    }

    private static String weak(Instant updatedAt, String id) {
        long epochMilli = updatedAt.toEpochMilli();
        String idShort = id.replace("-", "").substring(0, 8);
        return "W/\"" + epochMilli + "-" + idShort + "\"";
    }
}
