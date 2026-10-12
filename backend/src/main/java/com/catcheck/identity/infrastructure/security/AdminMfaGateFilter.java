package com.catcheck.identity.infrastructure.security;

import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.AdminAccessGate;
import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Locale;

@Component
public class AdminMfaGateFilter extends OncePerRequestFilter implements AdminAccessGate {

    private static final String ADMIN_PREFIX = "/api/v1/admin";

    private final MfaTotpRepository totpRepository;
    private final MessageResolver messageResolver;
    private final LocaleResolver localeResolver;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AdminMfaGateFilter(MfaTotpRepository totpRepository,
                              MessageResolver messageResolver,
                              LocaleResolver localeResolver,
                              JsonMapper jsonMapper,
                              Clock clock) {
        this.totpRepository = totpRepository;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(ADMIN_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();

        if (principal instanceof SecurityPrincipal user && user.mfaLevel() == MfaLevel.TOTP) {
            filterChain.doFilter(request, response);
            return;
        }

        ErrorCode errorCode = principal instanceof SecurityPrincipal user && enrolled(user)
                ? IdentityErrorCode.ADMIN_TOTP_REQUIRED
                : IdentityErrorCode.TOTP_SETUP_REQUIRED;
        writeProblem(request, response, errorCode);
    }

    private boolean enrolled(SecurityPrincipal user) {
        return totpRepository.findByUserId(user.userId())
                .map(MfaTotp::isActive)
                .orElse(false);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        Locale locale = localeResolver.resolveLocale(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                errorCode.status(), messageResolver.resolve(errorCode.code(), locale));
        problem.setType(errorCode.typeUri());
        problem.setTitle(errorCode.code());
        problem.setProperty("errorCode", errorCode.code());
        problem.setProperty("timestamp", clock.instant());

        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
