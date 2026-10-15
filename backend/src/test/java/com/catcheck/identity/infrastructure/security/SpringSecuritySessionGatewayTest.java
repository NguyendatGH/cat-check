package com.catcheck.identity.infrastructure.security;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.infrastructure.ratelimit.RequestIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringSecuritySessionGatewayTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");
    private static final AuthPolicy POLICY = new AuthPolicy(Duration.ofMinutes(5), 5,
            Duration.ofMinutes(10), 6, 12, 8, 72, 3, Duration.ofMinutes(1), 5, Duration.ofMinutes(15),
            Duration.ofDays(30), Duration.ofHours(12), 5);

    private FindByIndexNameSessionRepository<MapSession> httpSessions;
    private DeviceSessionRepository devices;
    private MockHttpSession currentSession;
    private SpringSecuritySessionGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        httpSessions = mock(FindByIndexNameSessionRepository.class);
        devices = mock(DeviceSessionRepository.class);
        ObjectProvider<org.springframework.security.web.authentication.session.SessionAuthenticationStrategy>
                strategies = mock(ObjectProvider.class);
        ObjectProvider<FindByIndexNameSessionRepository<? extends org.springframework.session.Session>>
                sessionRepos = mock(ObjectProvider.class);
        when(sessionRepos.getIfAvailable()).thenReturn((FindByIndexNameSessionRepository) httpSessions);

        gateway = new SpringSecuritySessionGateway(strategies, sessionRepos, devices,
                new RequestIpResolver(""), POLICY, mock(AuditLogService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        MockHttpServletRequest request = new MockHttpServletRequest();
        currentSession = new MockHttpSession(null, "current");
        request.setSession(currentSession);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokingOneDeviceDeletesItsRealHttpSession() {
        String otherHash = SpringSecuritySessionGateway.hashSessionId("other");
        when(devices.findBySessionIdHash(otherHash)).thenReturn(Optional.of(device(otherHash)));
        when(httpSessions.findByPrincipalName(USER.toString())).thenReturn(live("current", "other"));

        gateway.revokeBySessionId(otherHash);

        verify(httpSessions).deleteById("other");
        verify(httpSessions, never()).deleteById("current");
    }

    @Test
    void revokeAllKillsEveryOtherRealSessionButNotTheRunningRequest() {
        when(httpSessions.findByPrincipalName(USER.toString()))
                .thenReturn(live("current", "laptop", "phone"));

        gateway.revokeAllByUserId(USER);

        verify(httpSessions).deleteById("laptop");
        verify(httpSessions).deleteById("phone");
        verify(httpSessions, never()).deleteById("current");
    }

    @Test
    void expiredSessionsLeftInTheDeviceTableAreNotCountedAsActive() {
        String current = SpringSecuritySessionGateway.hashSessionId("current");
        String other = SpringSecuritySessionGateway.hashSessionId("other");
        String ghost = SpringSecuritySessionGateway.hashSessionId("idle-timed-out");
        when(devices.findActiveByUserId(eq(USER), any()))
                .thenReturn(List.of(device(current), device(other), device(ghost)));
        when(httpSessions.findByPrincipalName(USER.toString())).thenReturn(live("current", "other"));

        assertThat(gateway.activeSessionIdsByUserId(USER)).containsExactlyInAnyOrder(current, other);
    }

    @Test
    void rememberMeChoiceSetsTheRealSessionLifetime() {
        gateway.establish(USER, "person@example.com", Set.of("USER"), true);
        assertThat(currentSession.getMaxInactiveInterval()).isEqualTo((int) Duration.ofDays(30).toSeconds());

        gateway.establish(USER, "person@example.com", Set.of("USER"), false);
        assertThat(currentSession.getMaxInactiveInterval()).isEqualTo((int) Duration.ofHours(12).toSeconds());
    }

    @Test
    void repeatedLoginOnExistingSessionRotatesIdBeforeInsertingDeviceRow() {
        gateway.establish(USER, "person@example.com", Set.of("USER"), true);
        gateway.establish(USER, "person@example.com", Set.of("USER"), true);

        ArgumentCaptor<DeviceSession> inserted = ArgumentCaptor.forClass(DeviceSession.class);
        verify(devices, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues().get(0).sessionIdHash())
                .isNotEqualTo(inserted.getAllValues().get(1).sessionIdHash());
    }

    /* ---------- C1: TOTP required => no authenticated session before step 2 ---------- */

    @Test
    void pendingMfaDoesNotAuthenticateTheSession() {
        gateway.beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);

        assertThat(gateway.currentUserId()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(gateway.pendingMfaUserId()).contains(USER);
        verify(devices, never()).insert(any());
    }

    @Test
    void pendingMfaSessionIsIndexedSoRevokeAllFindsIt() {
        gateway.beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);

        org.springframework.mock.web.MockHttpServletRequest request =
                (org.springframework.mock.web.MockHttpServletRequest)
                        ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        assertThat(request.getSession(false)
                .getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
                .isEqualTo(USER.toString());
    }

    @Test
    void pendingMfaDropsAnIdentityAlreadyInTheSession() {
        gateway.establish(UUID.randomUUID(), "other@example.com", Set.of("USER"), true);

        gateway.beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);

        assertThat(gateway.currentUserId()).isEmpty();
        assertThat(currentSession.isInvalid()).isTrue();
    }

    @Test
    void completingPendingMfaOpensATotpLevelSession() {
        gateway.beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);

        gateway.completePendingMfa();

        assertThat(gateway.currentUserId()).contains(USER);
        assertThat(((com.catcheck.identity.application.AuthPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).mfaLevel())
                .isEqualTo(com.catcheck.shared.security.MfaLevel.TOTP);
        assertThat(gateway.pendingMfaUserId()).isEmpty();
    }

    @Test
    void pendingMfaExpires() {
        gateway.beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);

        SpringSecuritySessionGateway later = gatewayAt(NOW.plus(SpringSecuritySessionGateway.PENDING_MFA_TTL));

        assertThat(later.pendingMfaUserId()).isEmpty();
        later.completePendingMfa();
        assertThat(later.currentUserId()).isEmpty();
    }

    /* ---------- H3: step-up window really expires ---------- */

    @Test
    void reauthWindowExpiresWithTheClock() {
        gateway.establish(USER, "person@example.com", Set.of("USER"), true);
        assertThat(gateway.isReauthenticated()).isFalse();

        gateway.markReauthenticated(300);

        assertThat(gateway.isReauthenticated()).isTrue();
        assertThat(gatewayAt(NOW.plusSeconds(299)).isReauthenticated()).isTrue();
        assertThat(gatewayAt(NOW.plusSeconds(300)).isReauthenticated()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private SpringSecuritySessionGateway gatewayAt(Instant instant) {
        ObjectProvider<org.springframework.security.web.authentication.session.SessionAuthenticationStrategy>
                strategies = mock(ObjectProvider.class);
        ObjectProvider<FindByIndexNameSessionRepository<? extends org.springframework.session.Session>>
                sessionRepos = mock(ObjectProvider.class);
        when(sessionRepos.getIfAvailable()).thenReturn((FindByIndexNameSessionRepository) httpSessions);
        return new SpringSecuritySessionGateway(strategies, sessionRepos, devices,
                new RequestIpResolver(""), POLICY, mock(AuditLogService.class),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static Map<String, MapSession> live(String... ids) {
        Map<String, MapSession> sessions = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            sessions.put(id, new MapSession(id));
        }
        return sessions;
    }

    private static DeviceSession device(String sessionIdHash) {
        return new DeviceSession(UUID.randomUUID(), USER, sessionIdHash, "Trinh duyet", "ua", null,
                true, NOW, NOW, NOW.plus(Duration.ofDays(30)), null, null);
    }
}
