package com.catcheck.shared.i18n;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "catcheck.i18n")
@Validated
public record I18nProperties(

        @NotNull
        Locale defaultLocale,

        @NotEmpty
        List<Locale> supportedLocales
) {
}
