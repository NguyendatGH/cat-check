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

    public static void authenticate(SecurityPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
