package com.catcheck.place.api;

import org.springframework.beans.PropertyAccessException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;

/**
 * Lỗi chuyển kiểu mặc định của Spring chép nguyên giá trị client gửi vào thông điệp
 * ({@code For input string: "..."}). Với toạ độ đó là dữ liệu vị trí, nên chỉ giữ tên trường.
 */
final class RedactedBindingErrorProcessor extends DefaultBindingErrorProcessor {

    @Override
    public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
        bindingResult.addError(new FieldError(bindingResult.getObjectName(), ex.getPropertyName(), null, true,
                new String[] {"typeMismatch"}, null, "invalid"));
    }
}
