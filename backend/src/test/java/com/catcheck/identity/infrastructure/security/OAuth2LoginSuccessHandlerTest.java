package com.catcheck.identity.infrastructure.security;

import com.catcheck.identity.application.MfaService;
import com.catcheck.identity.application.OAuthService;
import com.catcheck.identity.domain.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H1 — dang nhap Google khong duoc bo qua TOTP. */
class OAuth2LoginSuccessHandlerTest {

    private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final String SUCCESS_URL = "http://localhost:5173/auth/oauth/complete";

    private OAuthService oauthService;
    private MfaService mfaService;
    private SpringSecuritySessionGateway gateway;
    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        oauthService = mock(OAuthService.class);
        mfaService = mock(MfaService.class);
        gateway = mock(SpringSecuritySessionGateway.class);
        handler = new OAuth2LoginSuccessHandler(oauthService, mfaService, gateway, SUCCESS_URL);
        when(oauthService.findOrCreate(any(), any())).thenReturn(new OAuthService.OAuthResult(
                USER, EmailAddress.of("person@example.com"), "Person", Set.of("USER"), false));
    }

    @Test
    void googleLoginWithActiveTotpWaitsForTheSecondFactor() throws Exception {
        when(mfaService.totpEnabled(USER)).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, oidcAuthentication());

        verify(gateway).beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);
        verify(gateway, never()).establish(any(), any(), any(), anyBoolean());
        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_URL + "?mfa=required");
    }

    @Test
    void googleLoginWithoutTotpOpensTheSession() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, oidcAuthentication());

        verify(gateway).establish(USER, "person@example.com", Set.of("USER"), true);
        verify(gateway, never()).beginPendingMfa(any(), any(), any(), anyBoolean());
        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_URL);
    }

    @Test
    void googleLoginThroughATunnelStaysOnThePublicHost() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("cat-demo.trycloudflare.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, oidcAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/oauth/complete");
    }

    private static TestingAuthenticationToken oidcAuthentication() {
        OidcUser user = mock(OidcUser.class);
        when(user.getClaims()).thenReturn(Map.of("sub", "google-sub"));
        return new TestingAuthenticationToken(user, null);
    }
}
