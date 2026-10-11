package com.catcheck.shared.i18n;

import com.catcheck.shared.error.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mọi hằng {@link ErrorCode} phải có câu dịch vi + en trong các bundle mà {@link LocaleConfig}
 * nạp. Thiếu khoá thì {@code setUseCodeAsDefaultMessage(true)} trả nguyên tên enum cho client.
 */
class ErrorCodeMessageCoverageTest {

    private static final List<Locale> LOCALES = List.of(Locale.forLanguageTag("vi"), Locale.ENGLISH);

    @Test
    void everyErrorCodeHasAVietnameseAndEnglishMessage() {
        MessageSource messages = new LocaleConfig(
                new I18nProperties(Locale.forLanguageTag("vi"), LOCALES)).messageSource();

        List<String> missing = new ArrayList<>();
        for (JavaClass type : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.catcheck")) {
            if (!type.isEnum() || !type.isAssignableTo(ErrorCode.class)) {
                continue;
            }
            for (Object constant : type.reflect().getEnumConstants()) {
                String code = ((ErrorCode) constant).code();
                for (Locale locale : LOCALES) {
                    if (code.equals(messages.getMessage(code, null, locale))) {
                        missing.add(type.getSimpleName() + "." + code + " [" + locale + "]");
                    }
                }
            }
        }

        assertThat(missing).isEmpty();
    }

    /**
     * Có tham số thì Spring dùng {@code MessageFormat}, và một dấu nháy đơn đứng lẻ mở một đoạn
     * "trích nguyên văn": dấu nháy biến mất (tiếng Anh: "code's" ra "codes"). Hai khoá dưới đây
     * được ném KÈM tham số ({@code ActivateCreditCodeService}, {@code CatNoteService}).
     */
    @Test
    void messagesThrownWithArgumentsKeepTheirApostrophes() {
        MessageSource messages = new LocaleConfig(
                new I18nProperties(Locale.forLanguageTag("vi"), LOCALES)).messageSource();

        for (String key : List.of("ACTIVATION_CODE_EXPIRED", "CAT_ARCHIVED")) {
            String raw = messages.getMessage(key, null, Locale.ENGLISH);
            String rendered = messages.getMessage(key, new Object[]{"X"}, Locale.ENGLISH);

            assertThat(rendered).as(key).isEqualTo(raw.replace("{0}", "X"));
        }
    }
}
