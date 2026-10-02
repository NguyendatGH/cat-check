package com.catcheck.content.api;

import com.catcheck.shared.error.BusinessRuleException;

import java.util.Arrays;

/**
 * Chuyển query param thành enum của domain mà vẫn trả lỗi chuẩn.
 *
 * <p><b>Vì sao không khai báo thẳng kiểu enum trong controller.</b> ArchUnit R4 cấm
 * {@code @RestController} có tham số hoặc kiểu trả về nằm trong package {@code ..domain..} — và mẫu
 * {@code ..domain..} khớp domain của MỌI module, nên {@code CareTipCategory} không thể xuất hiện
 * trong chữ ký của controller. Tương tự, R14 bắt mọi class trong {@code ..api.dto..} phải là
 * record, nên cũng không đặt được enum "bản sao" ở đó.</p>
 *
 * <p>Vì vậy controller nhận {@code String} và gọi {@link #parse(Class, String, String)}. Lỗi
 * chuyển thành {@code 400 VALIDATION_FAILED} chứ không để Jackson ném
 * {@code MethodArgumentTypeMismatchException} — client nhận được mã lỗi nghiệp vụ của hệ thống
 * thay vì một lỗi framework không nằm trong hợp đồng p8.</p>
 *
 * <p>Bản sao của lớp này tồn tại ở {@code cat.api.EnumParam}. Nên gộp lên
 * {@code shared.web} nhưng {@code shared} thuộc W3, không phải A3 — xem
 * {@code docs/handovers/A3.md}.</p>
 */
final class EnumParam {

    private EnumParam() {
    }

    /**
     * @param type      kiểu enum của domain
     * @param raw       giá trị query param, có thể {@code null} khi client không gửi
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
                        ContentErrorCode.VALIDATION_FAILED,
                        "Giá trị không hợp lệ cho tham số '" + paramName + "': " + trimmed));
    }
}
