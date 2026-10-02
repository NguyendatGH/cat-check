package com.catcheck.shared.error;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kiểm tra trùng mã lỗi ({@link ErrorCode#code()}) lúc khởi động ứng dụng.
 *
 * <p>Ở M0 chưa có module nghiệp vụ nào định nghĩa {@link ErrorCode} thật nên danh sách được
 * Spring inject vào đây sẽ RỖNG — {@link #validate} pass ngay vì không có gì để trùng, đây là
 * hành vi ĐÚNG (danh sách rỗng không phải bug). Từ M1+, mỗi module nên expose enum ErrorCode
 * của mình qua một {@code @Bean List<ErrorCode>} (ví dụ {@code List.of(ScanErrorCode.values())})
 * để Spring gom tất cả instance lại và registry này kiểm tra trùng mã ngay khi context khởi
 * động — trùng mã sẽ khiến app KHÔNG khởi động được (fail-fast).</p>
 */
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
