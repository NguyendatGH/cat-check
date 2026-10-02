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

/**
 * {@link AuthenticatedSessionGateway} + {@link AuthenticatedSessionRevoker} tren Spring
 * Security (p11 §11.1.3).
 *
 * <p><b>Phien that su nam o Spring Session JDBC</b> ({@code SPRING_SESSION} /
 * {@code SPRING_SESSION_ATTRIBUTES}); {@code user_device_session} chi la ban sao de
 * man hinh "thiet bi dang dang nhap" hien thi. {@link #establish} ghi ca hai.</p>
 *
 * <p><b>Session fixation (p11 §11.1.3)</b>: goi {@link SessionAuthenticationStrategy}
 * <b>truoc</b> khi gan danh tinh. No doi {@code sessionId} cua phien dang ton tai
 * (hoac tao phien moi neu chua co) nen attacker da len trinh duoc session id truoc do
 * khong con gia tri nao sau khi nguoi dung dang nhap.</p>
 *
 * <p>Lay {@code HttpServletRequest} qua {@link RequestContextHolder} thay vi truyen
 * tu controller: cong {@link AuthenticatedSessionGateway} nam o {@code ..domain..} nen
 * khong duoc mang kieu servlet (R1), con controller thi khong nen phai biet chi tiet
 * phien.</p>
 */
@Component
public class SpringSecuritySessionGateway implements AuthenticatedSessionGateway, AuthenticatedSessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SpringSecuritySessionGateway.class);

    private static final int DEVICE_LABEL_MAX = 200;
    private static final int USER_AGENT_MAX = 512;

    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final DeviceSessionRepository sessionRepository;
    private final RequestIpResolver ipResolver;
    private final AuthPolicy policy;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public SpringSecuritySessionGateway(
            org.springframework.beans.factory.ObjectProvider<SessionAuthenticationStrategy>
                    sessionAuthenticationStrategyProvider,
            DeviceSessionRepository sessionRepository,
            RequestIpResolver ipResolver,
            AuthPolicy policy,
            AuditLogService auditLogService,
            Clock clock) {
        // Spring Security tu dang ky SessionAuthenticationStrategy cho chuoi filter
        // dang chay; lay qua ObjectProvider de bean nay khong phu thuoc thu tu khoi tao.
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategyProvider.getIfAvailable();
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

        // 1) Doi session id truoc — session fixation defence.
        if (sessionAuthenticationStrategy != null) {
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        } else {
            // Khong co strategy (vi du test don le) -> van phai co phien de luu context.
            request.getSession(true);
        }
        // 2) Luu context vao phien.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // 3) Ban sao de hien thi. Session id lay TU phien vua doi, khong phai id cu.
        HttpSession session = request.getSession(false);
        String sessionId = session == null ? null : session.getId();
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
                    now.plus(rememberMe ? policy.sessionMaxInactive() : policy.sessionDefaultMaxInactive()),
                    null,
                    null));
        }
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
                principal.withReauthenticated(true), null, authentication.getAuthorities()));
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
        return currentPrincipal().map(AuthPrincipal::reauthenticated).orElse(Boolean.FALSE);
    }

    /* ---------- AuthenticatedSessionRevoker ---------- */

    @Override
    public int revokeAllByUserId(UUID userId) {
        Instant now = clock.instant();
        int revoked = sessionRepository.revokeAllByUserId(
                userId, now, SessionRevokeReason.PASSWORD_CHANGED);
        return revoked;
    }

    @Override
    public void revokeBySessionId(String sessionIdHash) {
        sessionRepository.findBySessionIdHash(sessionIdHash).ifPresent(session ->
                sessionRepository.revoke(session.id(), clock.instant(),
                        SessionRevokeReason.USER_REVOKE_ONE));
    }

    @Override
    public Set<String> activeSessionIdsByUserId(UUID userId) {
        return sessionRepository.findActiveByUserId(userId, clock.instant()).stream()
                .map(DeviceSession::sessionIdHash)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /* ---------- Helper ---------- */

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

    /**
     * Chua bao gio luu {@code sessionId} tho trong DB (p4 §A6: {@code session_id_hash}
     * = SHA-256). Session id la thong tin dinh danh phien — bao lo la bang chung
     * dùng chung voi cookie.
     */
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
        // Bug that da sua: substring(0, DEVICE_LABEL_MAX) nem StringIndexOutOfBoundsException
        // vi chuoi "Trinh duyet tren <os>" luon NGAN HON 200 ky tu — DEVICE_LABEL_MAX la TRAN
        // tren (phong khi sau nay them chi tiet dai hon), khong phai do dai co dinh. Xac nhan
        // that: crash ngay lan dau establish() thuc su duoc goi (truoc do khong ai goi toi vi
        // register() thieu buoc nay — xem AuthController.register()).
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
