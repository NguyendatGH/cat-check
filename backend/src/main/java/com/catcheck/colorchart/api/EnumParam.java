package com.catcheck.colorchart.api;

import com.catcheck.shared.error.BusinessRuleException;

import java.util.Arrays;

final class EnumParam {

    private EnumParam() {
    }

    static <E extends Enum<E>> E parse(Class<E> type, String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return Arrays.stream(type.getEnumConstants())
                .filter(candidate -> candidate.name().equalsIgnoreCase(trimmed))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(ColorChartErrorCode.VALIDATION_FAILED, paramName));
    }
}
