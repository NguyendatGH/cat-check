package com.catcheck.shared.error;

import com.catcheck.shared.i18n.MessageResolver;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug thật đã sửa (phát hiện khi làm G3 — rate limit kích hoạt mã credit,
 * {@code docs/cat-rag-credit-pipeline.md} B2): {@link GlobalExceptionHandler} xử lý
 * {@link RateLimitedException} qua handler chung của {@link CatCheckException} và chưa bao giờ
 * đọc {@link RateLimitedException#retryAfterSeconds()} — mọi {@code 429} của ứng dụng (identity
 * login/OTP/MFA/reset mật khẩu, và nay là kích hoạt mã credit) thiếu header {@code Retry-After}
 * dù p8 §8.3.5(d)/p16 NFR-40 yêu cầu header này bắt buộc 100%.
 */
class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            new MessageResolver(new StaticMessageSource()), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rateLimitedExceptionGetsRetryAfterHeaderWithTheExactSecondsGiven() {
        RateLimitedException ex = new RateLimitedException(FakeErrorCode.TOO_FAST, 42L, "SOME_RULE");

        ResponseEntity<ProblemDetail> response = handler.handleRateLimited(ex, Locale.ENGLISH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(429);
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", "TOO_FAST");
    }

    @Test
    void retryAfterSecondsIsFlooredToAtLeastOne() {
        RateLimitedException ex = new RateLimitedException(FakeErrorCode.TOO_FAST, 0L);

        ResponseEntity<ProblemDetail> response = handler.handleRateLimited(ex, Locale.ENGLISH);

        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    }

    @Test
    void noRetryAfterHeaderWhenSecondsAreUnknown() {
        RateLimitedException ex = new RateLimitedException(FakeErrorCode.TOO_FAST, null);

        ResponseEntity<ProblemDetail> response = handler.handleRateLimited(ex, Locale.ENGLISH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    void unexpectedExceptionLogsTypesAndStackFramesButNoMessageThatCouldCarryRequestData() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.handleUnexpected(new IllegalStateException("email=person@example.com",
                    new IllegalArgumentException("Key (email)=(person@example.com) already exists")), Locale.ENGLISH);

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("IllegalStateException");
                assertThat(event.getFormattedMessage()).doesNotContain("person@example.com");
                IThrowableProxy proxy = event.getThrowableProxy();
                assertThat(proxy).isNotNull();
                assertThat(proxy.getMessage()).isEqualTo(IllegalStateException.class.getName());
                assertThat(proxy.getStackTraceElementProxyArray()).isNotEmpty();
                assertThat(proxy.getCause()).isNotNull();
                assertThat(proxy.getCause().getMessage()).isEqualTo(IllegalArgumentException.class.getName());
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void malformedJsonBodyIs400ValidationFailedWithoutParserDetails() throws Exception {
        mockMvc().perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"person@example.com\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("person@example.com"))))
                .andExpect(content().string(not(containsString("JSON parse error"))));
    }

    @Test
    void beanValidationFailureCarriesTheSameErrorCodeAsOther400s() throws Exception {
        mockMvc().perform(post("/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new EchoEndpoint())
                .setControllerAdvice(handler)
                .setValidator(new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean())
                .build();
    }

    @RestController
    static class EchoEndpoint {

        @PostMapping("/echo")
        String echo(@Valid @RequestBody EchoRequest request) {
            return request.name();
        }
    }

    record EchoRequest(@NotBlank String name) {
    }

    private enum FakeErrorCode implements ErrorCode {
        TOO_FAST;

        @Override
        public String code() {
            return "TOO_FAST";
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.TOO_MANY_REQUESTS;
        }

        @Override
        public URI typeUri() {
            return URI.create("https://catcheck.vn/problems/too-fast");
        }
    }
}
