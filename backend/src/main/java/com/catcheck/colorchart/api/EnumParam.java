package com.catcheck.colorchart.api;

import com.catcheck.shared.error.BusinessRuleException;

import java.util.Arrays;

/**
 * Chuyển query param thành enum của domain mà vẫn trả lỗi chuẩn.
 *
 * <b>Vì sao không khai báo thẳng kiểu enum trong controller.</b> ArchUnit R4 cấm
 * {@code @RestController} có tham số hoặc kiểu trả về nằm trong package {@code ..domain..} — và mẫu
 * {@code ..domain..} khớp domain của MỌI module. Vì vậy controller nhận {@code String} và gọi
 * {@link #parse(Class, String, String)}.
 *
 * <p>Bản sao của lớp này tồn tại ở {@code content.api.EnumParam} và {@code cat.api.EnumParam}.
 * Nên gộp lên {@code shared.web} nhưng {@code shared} thuộc W3 — xem docs/handovers/A5.md.
 */
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
                        ColorChartErrorCode.VALIDATION_FAILED,
                        "Giá trị không hợp lệ cho tham số '" + paramName + "': " + trimmed));
    }
}
