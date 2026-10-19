package com.catcheck.shared.error;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ErrorCodeRegistry {

    public ErrorCodeRegistry(List<ErrorCode> registeredErrorCodes) {
        validate(registeredErrorCodes);
    }

    static void validate(List<ErrorCode> errorCodes) {
        Map<String, ErrorCode> seenByCode = new HashMap<>();
        for (ErrorCode errorCode : errorCodes) {
            ErrorCode existing = seenByCode.putIfAbsent(errorCode.code(), errorCode);
            if (existing != null && existing != errorCode) {
                throw new IllegalStateException(
                        "Trùng mã lỗi '%s' giữa %s và %s".formatted(
                                errorCode.code(), existing.getClass().getName(), errorCode.getClass().getName()));
            }
        }
    }
}
