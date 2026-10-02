package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
/**
 * Thân request {@code POST /api/v1/activations} (p8 H1, bảng §8.4.3(a)).
 *
 * <p>Regex ở đây chỉ chặn CHIỀU DÀI và ký tự không hợp lệ, <b>không</b> kiểm tra checksum:
 * checksum phải kiểm ở tầng nghiệp vụ ({@link ActivationCodeFormat#validateOrNull}) để thông điệp
 * lỗi và mã lỗi {@code ACTIVATION_CODE_MALFORMED} đến từ một chỗ. Nếu để regex ở đây thì
 * {@code @Valid} trả về lỗi 400 dạng field-level chung chung, mất đúng mã lỗi nghiệp vụ.</p>
 *
 * @param code mã thô người dùng nhập hoặc quét từ QR; tối đa 40 ký tự cho phép có khoảng
 *             trắng thừa khi gõ tay
 */
public record ActivateCreditCodeRequest(
        @NotBlank(message = "{credit.activation.code.required}")
        @Size(max = 40, message = "{credit.activation.code.too-long}")
        @Pattern(
                regexp = "^[\\x20-\\x7E]+$",
                message = "{credit.activation.code.malformed}")
        String code
) {
}
