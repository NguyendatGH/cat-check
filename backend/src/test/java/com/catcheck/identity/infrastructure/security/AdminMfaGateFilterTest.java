package com.catcheck.identity.infrastructure.security;

import com.catcheck.identity.application.AuthPrincipal;
import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.TotpStatus;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.MfaLevel;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * p8 §8.4.12 điều kiện chung #1 + p11 §11.12.1: mọi {@code /api/v1/admin/**} đòi phiên có
 * {@code mfaLevel = TOTP}.
 *
 * <p>Ba nhánh phải phân biệt được, vì FE xử lý khác nhau: đã enroll mà phiên chưa qua TOTP ⇒
 * {@code ADMIN_TOTP_REQUIRED} (chỉ cần mở hộp nhập mã); chưa enroll ⇒
 * {@code TOTP_SETUP_REQUIRED} (điều hướng cứng sang {@code /admin/setup-2fa}, p14 §14.5.3); đã
 * qua TOTP ⇒ đi tiếp.</p>
 */
class AdminMfaGateFilterTest {

    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-00000000a001");

    private FakeTotpRepository totpRepository;
    private AdminMfaGateFilter filter;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("ADMIN_TOTP_REQUIRED", Locale.ENGLISH, "totp required");
        messages.addMessage("TOTP_SETUP_REQUIRED", Locale.ENGLISH, "totp setup required");
        totpRepository = new FakeTotpRepository();
        filter = new AdminMfaGateFilter(
                totpRepository,
                new MessageResolver(messages),
                new FixedLocaleResolver(Locale.ENGLISH),
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Admin đã enroll TOTP nhưng phiên mfaLevel=NONE ⇒ 403 ADMIN_TOTP_REQUIRED, không đi tiếp")
    void blocksAdminSessionThatHasNotPassedTotp() throws Exception {
        totpRepository.put(ADMIN_ID, TotpStatus.ACTIVE);
        authenticate(MfaLevel.NONE);

        MockHttpServletResponse response = filter("/api/v1/admin/activation-codes");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"ADMIN_TOTP_REQUIRED\"");
    }

    @Test
    @DisplayName("Admin CHƯA enroll TOTP ⇒ 403 TOTP_SETUP_REQUIRED (FE điều hướng /admin/setup-2fa)")
    void blocksAdminWithoutTotpEnrollment() throws Exception {
        authenticate(MfaLevel.NONE);

        MockHttpServletResponse response = filter("/api/v1/admin/users");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"TOTP_SETUP_REQUIRED\"");
    }

    @Test
    @DisplayName("Bản ghi TOTP còn PENDING vẫn tính là chưa enroll ⇒ TOTP_SETUP_REQUIRED")
    void pendingEnrollmentIsNotEnrolled() throws Exception {
        totpRepository.put(ADMIN_ID, TotpStatus.PENDING);
        authenticate(MfaLevel.NONE);

        MockHttpServletResponse response = filter("/api/v1/admin/users");

        assertThat(response.getContentAsString()).contains("\"errorCode\":\"TOTP_SETUP_REQUIRED\"");
    }

    @Test
    @DisplayName("Phiên đã qua TOTP ⇒ đi tiếp, filter không ghi gì vào response")
    void allowsSessionWithTotpLevel() throws Exception {
        totpRepository.put(ADMIN_ID, TotpStatus.ACTIVE);
        authenticate(MfaLevel.TOTP);

        RecordingChain chain = new RecordingChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/admin/jobs/runs"), response, chain);

        assertThat(chain.called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("Path ngoài /api/v1/admin không bị gác — đường nghiệp vụ không cần TOTP")
    void ignoresNonAdminPaths() throws Exception {
        authenticate(MfaLevel.NONE);

        RecordingChain chain = new RecordingChain();
        filter.doFilter(request("/api/v1/credits/balance"), new MockHttpServletResponse(), chain);

        assertThat(chain.called).isTrue();
    }

    @Test
    @DisplayName("Không có principal kiểu SecurityPrincipal ⇒ chặn, vì không chứng minh được đã qua TOTP")
    void blocksWhenPrincipalTypeIsUnknown() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_SUPER"))));

        MockHttpServletResponse response = filter("/api/v1/admin/users");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"TOTP_SETUP_REQUIRED\"");
    }

    /* ---------------------------------------------------------------- helper */

    private MockHttpServletResponse filter(String path) throws Exception {
        RecordingChain chain = new RecordingChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(path), response, chain);
        assertThat(chain.called).as("filter phải chặn, không được gọi chain").isFalse();
        return response;
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private static void authenticate(MfaLevel mfaLevel) {
        AuthPrincipal principal = new AuthPrincipal(
                ADMIN_ID, "admin@catcheck.vn", Set.of("ADMIN_SUPER"),
                Instant.parse("2026-10-03T07:00:00Z"), false, mfaLevel);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN_SUPER"))));
    }

    private static final class RecordingChain implements FilterChain {

        boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            called = true;
        }
    }

    /** {@code user_mfa_totp} một dòng — chỉ hai phương thức mà bộ gác dùng là có nghĩa. */
    private static final class FakeTotpRepository implements MfaTotpRepository {

        private final java.util.Map<UUID, MfaTotp> rows = new java.util.HashMap<>();

        void put(UUID userId, TotpStatus status) {
            Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
            rows.put(userId, new MfaTotp(
                    userId, new byte[]{1}, status, "SHA1", 6, 30,
                    null, 0, null,
                    status == TotpStatus.PENDING ? createdAt.plusSeconds(600) : null,
                    status == TotpStatus.ACTIVE ? createdAt : null,
                    1, null, null, createdAt, createdAt));
        }

        @Override
        public Optional<MfaTotp> findByUserId(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public void insert(MfaTotp totp) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markActive(UUID userId, Instant activatedAt, long lastUsedStep) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateLastUsedStep(UUID userId, long step) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int incrementFailedCount(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetFailedCount(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void lock(UUID userId, Instant lockedUntil) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countActive() {
            return rows.values().stream().filter(MfaTotp::isActive).count();
        }
    }
}
