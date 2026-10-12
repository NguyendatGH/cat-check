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

final class ETag {

    private ETag() {
    }

    static String of(ColorChart chart) {
        return weak(chart.getUpdatedAt(), chart.getId().toString());
    }

    static String of(PhClassificationBand band) {
        return weak(band.getUpdatedAt(), band.getId().toString());
    }

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
