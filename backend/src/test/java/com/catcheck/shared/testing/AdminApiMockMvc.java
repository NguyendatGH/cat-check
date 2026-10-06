package com.catcheck.shared.testing;

import com.catcheck.shared.error.GlobalExceptionHandler;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.SecurityPrincipal;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Dựng {@link MockMvc} <b>standalone</b> cho test tầng api của nhóm L.
 *
 * <p>Standalone (không {@code @SpringBootTest}) là chủ ý: nhánh cần kiểm là <i>vai trò</i>,
 * <i>{@code If-Match}</i> và <i>mã lỗi RFC7807</i> — ba thứ nằm trọn trong controller +
 * {@link GlobalExceptionHandler}. Nạp cả context chỉ để kiểm điều đó khiến mỗi lớp test tốn
 * hàng chục giây và kéo theo DB thật.
 *
 * <p>Hai thứ phải tự nối vì standalone không có chúng:</p>
 * <ul>
 *   <li>{@link AuthenticationPrincipalArgumentResolver} — {@code @CurrentUser} là meta-annotation
 *       của {@code @AuthenticationPrincipal}; thiếu resolver thì tham số về {@code null} và mọi
 *       test đều thành NPE 500 thay vì 403.</li>
 *   <li>{@link GlobalExceptionHandler} — không có nó thì {@code CatCheckException} thoát ra dạng
 *       500 và test không phân biệt được 409 với 422.</li>
 * </ul>
 */
public final class AdminApiMockMvc {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private AdminApiMockMvc() {
    }

    public static MockMvc build(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(
                        new MessageResolver(new StaticMessageSource()), CLOCK))
                .setValidator(new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean())
                .build();
    }

    /** Đặt principal cho request kế tiếp. Gọi {@link #clear()} ở {@code @AfterEach}. */
    public static void authenticate(SecurityPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
