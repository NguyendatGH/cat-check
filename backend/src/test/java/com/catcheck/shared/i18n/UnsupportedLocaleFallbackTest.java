package com.catcheck.shared.i18n;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.shared.error.GlobalExceptionHandler;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Clock;

import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accept-Language không được hỗ trợ (vd {@code fr}) phải rơi về tiếng Việt trong {@code detail}
 * của problem-detail do {@link GlobalExceptionHandler} tạo; {@code en} vẫn ra tiếng Anh để chứng
 * minh resolver đang chọn theo header chứ không phải luôn trả vi.
 */
class UnsupportedLocaleFallbackTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocaleConfig.class)
            .withPropertyValues(
                    "catcheck.i18n.default-locale=vi",
                    "catcheck.i18n.supported-locales=vi,en");

    @Test
    void unsupportedAcceptLanguageFallsBackToViInProblemDetail() {
        runner.run(context -> {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MissingPlaceEndpoint())
                    .setControllerAdvice(new GlobalExceptionHandler(
                            new MessageResolver(context.getBean(MessageSource.class)), Clock.systemUTC()))
                    .setLocaleResolver(context.getBean(LocaleResolver.class))
                    .build();

            mockMvc.perform(get("/missing").header(ACCEPT_LANGUAGE, "fr"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PLACE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Không tìm thấy địa điểm này."));

            mockMvc.perform(get("/missing").header(ACCEPT_LANGUAGE, "en"))
                    .andExpect(jsonPath("$.detail").value("This place could not be found."));
        });
    }

    @RestController
    static class MissingPlaceEndpoint {

        @GetMapping("/missing")
        void missing() {
            throw new NotFoundException(PlaceErrorCode.PLACE_NOT_FOUND);
        }
    }
}
