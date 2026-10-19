package com.catcheck.shared.i18n;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
@EnableConfigurationProperties(I18nProperties.class)
public class LocaleConfig {

    private final I18nProperties i18nProperties;

    public LocaleConfig(I18nProperties i18nProperties) {
        this.i18nProperties = i18nProperties;
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(
                "classpath:messages/messages",
                "classpath:messages/errors",
                "classpath:messages/identity",
                "classpath:messages/privacy",
                "classpath:messages/cat",
                "classpath:messages/credit",
                "classpath:messages/colorchart",
                "classpath:messages/scan",
                "classpath:messages/insight",
                "classpath:messages/export",
                "classpath:messages/reminder",
                "classpath:messages/notification",
                "classpath:messages/community",
                "classpath:messages/shop",
                "classpath:messages/ai",
                "classpath:messages/content",
                "classpath:messages/admin",
                "classpath:messages/media",
                "classpath:messages/place");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(i18nProperties.defaultLocale());
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(i18nProperties.defaultLocale());
        localeResolver.setSupportedLocales(i18nProperties.supportedLocales());
        return localeResolver;
    }
}
