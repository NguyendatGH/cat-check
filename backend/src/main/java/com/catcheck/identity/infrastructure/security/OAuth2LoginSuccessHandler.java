package com.catcheck.identity.infrastructure.security;

import com.catcheck.identity.application.AuthRequestContext;
import com.catcheck.identity.application.MfaService;
import com.catcheck.identity.application.OAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
public class OAuth2LoginSuccessHandler
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final OAuthService oauthService;
    private final MfaService mfaService;
    private final SpringSecuritySessionGateway sessionGateway;
    private final String frontendSuccessUrl;

    public OAuth2LoginSuccessHandler(
            OAuthService oauthService,
            MfaService mfaService,
            SpringSecuritySessionGateway sessionGateway,
            @Value("${catcheck.oauth2.frontend-success-url:http://localhost:5173/auth/oauth/complete}")
            String frontendSuccessUrl) {
        this.oauthService = oauthService;
        this.mfaService = mfaService;
        this.sessionGateway = sessionGateway;
        this.frontendSuccessUrl = frontendSuccessUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            log.warn("Google OAuth authenticated with an unexpected principal type: {}",
                    authentication.getPrincipal().getClass().getName());
            redirectWithError(request, response);
            return;
        }

        AuthRequestContext context = AuthRequestContext.of(
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));

        try {
            OAuthService.OAuthResult result = oauthService.findOrCreate(oidcUser.getClaims(), context);
            if (mfaService.totpEnabled(result.userId())) {
                sessionGateway.beginPendingMfa(result.userId(), result.email().value(), result.roles(), true);
                response.sendRedirect(withQuery(successUrl(request), "mfa=required"));
                return;
            }
            sessionGateway.establish(result.userId(), result.email().value(), result.roles(), true);
            response.sendRedirect(successUrl(request));
        } catch (RuntimeException ex) {
            log.warn("Google OAuth account mapping failed", ex);
            redirectWithError(request, response);
        }
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("Google OAuth authentication failed: {}", exception.getMessage());
        redirectWithError(request, response);
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect(withQuery(successUrl(request), "error=oauth_failed"));
    }

    /**
     * A loopback frontend URL only makes sense to a loopback client. When the login came in
     * through a public host (e.g. a Cloudflare tunnel, resolved from X-Forwarded-Host), keep
     * the path and let the container resolve it against that host instead of localhost.
     */
    private String successUrl(HttpServletRequest request) {
        URI configured = URI.create(frontendSuccessUrl);
        if (configured.getHost() == null
                || !isLoopback(configured.getHost())
                || isLoopback(request.getServerName())) {
            return frontendSuccessUrl;
        }
        String path = configured.getRawPath() == null || configured.getRawPath().isEmpty()
                ? "/" : configured.getRawPath();
        return configured.getRawQuery() == null ? path : path + "?" + configured.getRawQuery();
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || host.startsWith("127.")
                || "[::1]".equals(host)
                || "::1".equals(host);
    }

    private static String withQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }
}
