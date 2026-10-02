package com.catcheck.cat.api;

import com.catcheck.shared.error.BusinessRuleException;

import java.util.Arrays;

/**
 * Chuyển tham số dạng chuỗi (query param hoặc field trong request body) thành enum của domain mà
 * vẫn trả lỗi chuẩn — bản sao cục bộ của {@code content.api.EnumParam} (chính file đó ghi chú:
 * "Bản sao của lớp này tồn tại ở cat.api.EnumParam" — dự trù cho lúc {@code CatController} được
 * viết, nay mới thật sự tồn tại). Nên gộp lên {@code shared.web} nhưng đó là việc của W3.
 *
 * <p><b>Vì sao không khai enum thẳng trong chữ ký controller.</b> R4 cấm {@code @RestController}
 * có tham số/kiểu trả về nằm trong package {@code ..domain..} (mẫu khớp domain của MỌI module).
 * R14 bắt {@code ..api.dto..} phải là record nên cũng không đặt "enum bản sao" ở đó được. Vì vậy
 * controller nhận {@code String} rồi gọi {@link #parse(Class, String, String)} — lỗi thành
 * {@code 400 VALIDATION_FAILED} thay vì để Spring/Jackson ném lỗi framework không nằm trong hợp
 * đồng p8.</p>
 */
final class EnumParam {

    private EnumParam() {
    }

    /**
     * @param type      kiểu enum của domain
     * @param raw       giá trị thô, có thể {@code null} khi client không gửi
     * @param paramName tên tham số, chỉ để ghép thông điệp lỗi dễ đọc
     * @return enum tương ứng, hoặc {@code null} nếu {@code raw} rỗng
     * @throws BusinessRuleException {@code 400 VALIDATION_FAILED} nếu giá trị không thuộc tập hằng số
     */
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
