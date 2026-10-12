package com.catcheck.cat.api;

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
                .orElseThrow(() -> new BusinessRuleException(
                        CatErrorCode.VALIDATION_FAILED,
                        "Giá trị không hợp lệ cho tham số '" + paramName + "': " + trimmed));
    }
}
