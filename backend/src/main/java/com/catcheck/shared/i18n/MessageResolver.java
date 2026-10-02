package com.catcheck.shared.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Wrapper mỏng quanh {@link MessageSource} để tra message đã dịch theo mã + tham số + locale.
 * Dùng ở {@code GlobalExceptionHandler} và bất kỳ nơi nào cần trả text song ngữ (vi/en).
 */
@Component
public class MessageResolver {

    private final MessageSource messageSource;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String resolve(String code, Object[] args, Locale locale) {
        try {
            return messageSource.getMessage(code, args, locale);
        } catch (NoSuchMessageException ex) {
            // Không có bản dịch cho mã này — trả về chính mã thay vì ném lỗi, để không che lấp
            // lỗi nghiệp vụ gốc bằng một lỗi thiếu message.
            return code;
        }
    }

    public String resolve(String code, Locale locale) {
        return resolve(code, new Object[0], locale);
    }
}
