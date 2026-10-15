package com.catcheck.identity.infrastructure.security;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.application.AuthPrincipal;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.infrastructure.ratelimit.RequestIpResolver;
import com.catcheck.shared.security.MfaLevel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class SpringSecuritySessionGateway implements AuthenticatedSessionGateway, AuthenticatedSessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SpringSecuritySessionGateway.class);

    private static final int DEVICE_LABEL_MAX = 200;

    static final String PENDING_MFA_ATTRIBUTE = "catcheck.pendingMfaLogin";
    static final java.time.Duration PENDING_MFA_TTL = java.time.Duration.ofMinutes(5);
    private static final int USER_AGENT_MAX = 512;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final FindByIndexNameSessionRepository<? extends Session> httpSessions;
    private final DeviceSessionRepository sessionRepository;
    private final RequestIpResolver ipResolver;
    private final AuthPolicy policy;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public SpringSecuritySessionGateway(
            org.springframework.beans.factory.ObjectProvider<SessionAuthenticationStrategy>
                    sessionAuthenticationStrategyProvider,
            org.springframework.beans.factory.ObjectProvider<FindByIndexNameSessionRepository<? extends Session>>
                    httpSessionsProvider,
            DeviceSessionRepository sessionRepository,
            RequestIpResolver ipResolver,
            AuthPolicy policy,
            AuditLogService auditLogService,
            Clock clock) {
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategyProvider.getIfAvailable();
        this.httpSessions = httpSessionsProvider.getIfAvailable();
        this.sessionRepository = sessionRepository;
        this.ipResolver = ipResolver;
        this.policy = policy;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Override
    public void establish(UUID userId, String email, Set<String> roles, boolean rememberMe) {
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();
        Instant now = clock.instant();

        AuthPrincipal principal = AuthPrincipal.of(userId, email, roles, now);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities(roles));

        HttpSession previousSession = request.getSession(false);
        String previousSessionId = previousSession == null ? null : previousSession.getId();
        if (sessionAuthenticationStrategy != null) {
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        }
        HttpSession authenticatedSession = request.getSession(true);
        if (previousSessionId != null && previousSessionId.equals(authenticatedSession.getId())) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession(false);
        String sessionId = session == null ? null : session.getId();
        java.time.Duration maxInactive = rememberMe
                ? policy.sessionMaxInactive() : policy.sessionDefaultMaxInactive();
        if (session != null) {
            session.setMaxInactiveInterval(Math.toIntExact(maxInactive.toSeconds()));
        }
        if (sessionId != null) {
            sessionRepository.insert(new DeviceSession(
                    UUID.randomUUID(),
                    userId,
                    hashSessionId(sessionId),
                    deviceLabel(request),
                    userAgent(request),
                    ipResolver.resolve(request),
                    rememberMe,
                    now,
                    now,
                    now.plus(maxInactive),
                    null,
                    null));
        }
    }

    @Override
    public void beginPendingMfa(UUID userId, String email, Set<String> roles, boolean rememberMe) {
        HttpServletRequest request = currentRequest();
        // Bo moi danh tinh con trong phien cu (vd OAuth2AuthenticationToken) truoc khi cho buoc 2.
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        SecurityContextHolder.clearContext();
        request.getSession(true).setAttribute(PENDING_MFA_ATTRIBUTE, new PendingMfaLogin(
                userId, email, Set.copyOf(roles), rememberMe, clock.instant().plus(PENDING_MFA_TTL)));
    }

    @Override
    public Optional<UUID> pendingMfaUserId() {
        return pendingMfa().map(PendingMfaLogin::userId);
    }

    @Override
    public void completePendingMfa() {
        Optional<PendingMfaLogin> pending = pendingMfa();
        if (pending.isEmpty()) {
            return;
        }
        currentRequest().getSession(false).removeAttribute(PENDING_MFA_ATTRIBUTE);
        PendingMfaLogin login = pending.get();
        establish(login.userId(), login.email(), login.roles(), login.rememberMe());
        markMfaTotpVerified();
    }

    private Optional<PendingMfaLogin> pendingMfa() {
        HttpSession session = currentRequest().getSession(false);
        if (session == null
                || !(session.getAttribute(PENDING_MFA_ATTRIBUTE) instanceof PendingMfaLogin pending)) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(pending.expiresAt())) {
            session.removeAttribute(PENDING_MFA_ATTRIBUTE);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    record PendingMfaLogin(UUID userId, String email, Set<String> roles, boolean rememberMe,
                           Instant expiresAt) implements java.io.Serializable {
    }

    @Override
    public void invalidateCurrent() {
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();
        HttpSession session = request.getSession(false);
        String sessionId = session == null ? null : session.getId();
        if (sessionId != null) {
            sessionRepository.revoke(findIdBySessionIdHash(hashSessionId(sessionId)),
                    clock.instant(), SessionRevokeReason.USER_LOGOUT);
        }
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
    }

    @Override
    public void markReauthenticated(int windowSeconds) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return;
        }
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal.withReauthenticatedUntil(clock.instant().plusSeconds(windowSeconds)),
                null, authentication.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @Override
    public void markMfaTotpVerified() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return;
        }
        HttpServletRequest request = currentRequest();
        HttpServletResponse response = currentResponse();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal.withMfaLevel(MfaLevel.TOTP), null, authentication.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @Override
    public Optional<UUID> currentUserId() {
        return currentPrincipal().map(AuthPrincipal::userId);
    }

    @Override
    public Optional<String> currentEmail() {
        return currentPrincipal().map(AuthPrincipal::email);
    }

    @Override
    public Optional<String> currentSessionIdHash() {
        return currentRequest().getSession(false) == null
                ? Optional.empty()
                : Optional.of(hashSessionId(currentRequest().getSession(false).getId()));
    }

    @Override
    public boolean isReauthenticated() {
        Instant now = clock.instant();
        return currentPrincipal().map(principal -> principal.isReauthenticatedAt(now)).orElse(Boolean.FALSE);
    }



    @Override
    public int revokeAllByUserId(UUID userId) {
        Instant now = clock.instant();
        int revoked = sessionRepository.revokeAllByUserId(
                userId, now, SessionRevokeReason.PASSWORD_CHANGED);
        deleteHttpSessions(userId, sessionId -> true);
        return revoked;
    }

    @Override
    public void revokeBySessionId(String sessionIdHash) {
        sessionRepository.findBySessionIdHash(sessionIdHash).ifPresent(session -> {
            sessionRepository.revoke(session.id(), clock.instant(), SessionRevokeReason.USER_REVOKE_ONE);
            deleteHttpSessions(session.userId(), sessionId -> hashSessionId(sessionId).equals(sessionIdHash));
        });
    }

    @Override
    public Set<String> activeSessionIdsByUserId(UUID userId) {
        Set<String> recorded = sessionRepository.findActiveByUserId(userId, clock.instant()).stream()
                .map(DeviceSession::sessionIdHash)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (httpSessions == null) {
            return recorded;
        }
        return httpSessions.findByPrincipalName(userId.toString()).values().stream()
                .filter(session -> !session.isExpired())
                .map(session -> hashSessionId(session.getId()))
                .filter(recorded::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void deleteHttpSessions(UUID userId, java.util.function.Predicate<String> matches) {
        if (httpSessions == null) {
            return;
        }
        String currentSessionId = currentSessionIdOrNull();
        for (String sessionId : httpSessions.findByPrincipalName(userId.toString()).keySet()) {
            if (!sessionId.equals(currentSessionId) && matches.test(sessionId)) {
                httpSessions.deleteById(sessionId);
            }
        }
    }

    private static String currentSessionIdOrNull() {
        ServletRequestAttributes attributes = currentAttributes();
        HttpSession session = attributes == null ? null : attributes.getRequest().getSession(false);
        return session == null ? null : session.getId();
    }


    private Optional<AuthPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    private static List<GrantedAuthority> authorities(Set<String> roles) {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    private UUID findIdBySessionIdHash(String sessionIdHash) {
        return sessionRepository.findBySessionIdHash(sessionIdHash)
                .map(DeviceSession::id)
                .orElse(null);
    }

    static String hashSessionId(String sessionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sessionId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JCA khong co SHA-256", ex);
        }
    }

    private static String deviceLabel(HttpServletRequest request) {
        String userAgent = userAgent(request);
        if (userAgent.isBlank()) {
            return "Khong ro";
        }
        String os = userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") ? "iOS"
                : userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS") ? "macOS"
                : userAgent.contains("Linux") ? "Linux"
                : "Khong ro";
        String label = "Trinh duyet tren " + os;
        return label.substring(0, Math.min(DEVICE_LABEL_MAX, label.length()));
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= USER_AGENT_MAX ? value : value.substring(0, USER_AGENT_MAX);
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = currentAttributes();
        if (attributes == null) {
            throw new IllegalStateException(
                    "Khong co HTTP request dang chay. Session gateway chi duoc goi tu trong "
                            + "controller, khong duoc goi tu job/async.");
        }
        return attributes.getRequest();
    }

    private static HttpServletResponse currentResponse() {
        ServletRequestAttributes attributes = currentAttributes();
        if (attributes == null) {
            throw new IllegalStateException("Khong co HTTP response dang chay");
        }
        HttpServletResponse response = attributes.getResponse();
        if (response == null) {
            throw new IllegalStateException("Khong co HTTP response dang chay");
        }
        return response;
    }

    private static ServletRequestAttributes currentAttributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes
                : null;
    }
}
