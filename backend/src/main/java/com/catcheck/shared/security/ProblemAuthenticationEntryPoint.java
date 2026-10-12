package com.catcheck.shared.security;

import com.catcheck.shared.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;

public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_CODE = "UNAUTHENTICATED";
    private static final URI TYPE = URI.create("https://catcheck.vn/problems/unauthenticated");

    private final MessageResolver messageResolver;
    private final LocaleResolver localeResolver;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ProblemAuthenticationEntryPoint(MessageResolver messageResolver, LocaleResolver localeResolver,
                                           JsonMapper jsonMapper, Clock clock) {
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                messageResolver.resolve(ERROR_CODE, localeResolver.resolveLocale(request)));
        problem.setType(TYPE);
        problem.setTitle(ERROR_CODE);
        problem.setProperty("errorCode", ERROR_CODE);
        problem.setProperty("timestamp", clock.instant());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
