package com.catcheck.shared.error;

import com.catcheck.shared.i18n.MessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

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
