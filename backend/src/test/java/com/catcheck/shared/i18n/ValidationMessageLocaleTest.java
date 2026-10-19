package com.catcheck.shared.i18n;

import com.catcheck.cat.api.dto.CreateNoteRequest;
import com.catcheck.credit.api.dto.ActivateCreditCodeRequest;
import com.catcheck.shared.error.GlobalExceptionHandler;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Clock;

import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Message của bean-validation trong DTO ({@code message = "{key}"}) phải ra đúng ngôn ngữ của
 * request trong {@code violations[]}. Dùng đúng cặp validator Boot ({@link ValidationAutoConfiguration},
 * nối {@code messageSource} của {@link LocaleConfig}) + {@code LocaleResolver} của app, chỉ không
 * cần Docker/DB.
 */
class ValidationMessageLocaleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(LocaleConfig.class)
            .withPropertyValues(
                    "catcheck.i18n.default-locale=vi",
                    "catcheck.i18n.supported-locales=vi,en");

    @Test
    void sameInvalidRequestGetsViOrEnMessageByAcceptLanguage() {
        runner.run(context -> {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EchoEndpoint())
                    .setControllerAdvice(new GlobalExceptionHandler(
                            new MessageResolver(context.getBean(MessageSource.class)), Clock.systemUTC()))
                    .setValidator(context.getBean(LocalValidatorFactoryBean.class))
                    .setLocaleResolver(context.getBean(LocaleResolver.class))
                    .build();

            // Khoá nằm ở ValidationMessages_{vi,en}.properties (Hibernate Validator bundle).
            assertNoteMessage(mockMvc, "vi", "Nội dung ghi chú không được để trống");
            assertNoteMessage(mockMvc, "en", "Note content must not be empty");

            // Khoá nằm ở messages/credit_{vi,en}.properties (messageSource của app) — cùng cơ chế.
            mockMvc.perform(post("/activate").header(ACCEPT_LANGUAGE, "en")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations[?(@.message == 'Please enter an activation code.')]").exists());
        });
    }

    private static void assertNoteMessage(MockMvc mockMvc, String language, String expected) throws Exception {
        mockMvc.perform(post("/notes").header(ACCEPT_LANGUAGE, language)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("body"))
                .andExpect(jsonPath("$.violations[0].message").value(expected));
    }

    @RestController
    static class EchoEndpoint {

        @PostMapping("/notes")
        void note(@Valid @RequestBody CreateNoteRequest request) {
        }

        @PostMapping("/activate")
        void activate(@Valid @RequestBody ActivateCreditCodeRequest request) {
        }
    }
}
