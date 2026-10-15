package com.catcheck.identity;

import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.TotpCodeVerifier;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.shared.i18n.I18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties(I18nProperties.class)
@ActiveProfiles("test")
@Testcontainers
class AuthLoginSessionIntegrationTest {

    private static final String PASSWORD = "TestAuthPassword@123";
    private static final String TOTP_SECRET = "JBSWY3DPEHPK3PXP";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final int port;
    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;
    private final PiiCipher piiCipher;
    private final Clock clock;

    AuthLoginSessionIntegrationTest(@LocalServerPort int port,
                                    @Autowired JdbcTemplate jdbc,
                                    @Autowired PasswordHasher passwordHasher,
                                    @Autowired PiiCipher piiCipher,
                                    @Autowired Clock clock) {
        this.port = port;
        this.jdbc = jdbc;
        this.passwordHasher = passwordHasher;
        this.piiCipher = piiCipher;
        this.clock = clock;
    }

    @Test
    void adminCanLogInTwiceInOneBrowserAndCompleteTotp() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "auth-relogin-" + userId + "@example.test";
        jdbc.update("""
                INSERT INTO app_user (id, email, full_name, status, email_verified_at)
                VALUES (?, ?, 'Auth integration test', 'ACTIVE', now())
                """, userId, email);
        jdbc.update("""
                INSERT INTO user_identity (user_id, provider, provider_user_id,
                                           provider_email, email_verified, password_hash)
                VALUES (?, 'LOCAL', ?, ?, true, ?)
                """, userId, email, email, passwordHasher.hash(PASSWORD));
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'ADMIN_SUPER')", userId);
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'USER')", userId);
        byte[] encryptedSecret = piiCipher.encrypt(CryptoPurpose.MFA_TOTP,
                PiiCipher.aad("user_mfa_totp", "secret_enc", userId),
                TOTP_SECRET.getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO user_mfa_totp (user_id, secret_enc, status, algorithm,
                                           digits, period_seconds, activated_at, key_version)
                VALUES (?, ?, 'ACTIVE', 'SHA1', 6, 30, now(), ?)
                """, userId, encryptedSecret, piiCipher.currentWriteKeyVersion());

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        URI base = URI.create("http://localhost:" + port);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"rememberMe\":true}";

        for (int attempt = 0; attempt < 2; attempt++) {
            String csrf = csrfToken(client, cookies, base);
            HttpResponse<String> login = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(login.statusCode()).as("login attempt " + (attempt + 1)).isEqualTo(200);
            assertThat(login.body()).contains("\"mfaRequired\":true", "TOTP");
        }

        String code = TotpCodeVerifier.now("SHA1", 6, TOTP_SECRET,
                clock.instant().getEpochSecond() / 30);
        HttpResponse<String> verified = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/auth/totp/verify"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken(client, cookies, base))
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"" + code + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(verified.statusCode()).isEqualTo(200);
        assertThat(verified.body()).contains("\"mfaLevel\":\"TOTP\"");
        HttpResponse<String> session = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/auth/session"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(session.body()).contains("\"authenticated\":true", "\"ADMIN_SUPER\"", "\"USER\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_device_session WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void failedPasswordAndTotpAttemptsAreCommittedNotRolledBack() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "auth-failcount-" + userId + "@example.test";
        jdbc.update("""
                INSERT INTO app_user (id, email, full_name, status, email_verified_at)
                VALUES (?, ?, 'Auth integration test', 'ACTIVE', now())
                """, userId, email);
        jdbc.update("""
                INSERT INTO user_identity (user_id, provider, provider_user_id,
                                           provider_email, email_verified, password_hash)
                VALUES (?, 'LOCAL', ?, ?, true, ?)
                """, userId, email, email, passwordHasher.hash(PASSWORD));
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, 'USER')", userId);
        byte[] encryptedSecret = piiCipher.encrypt(CryptoPurpose.MFA_TOTP,
                PiiCipher.aad("user_mfa_totp", "secret_enc", userId),
                TOTP_SECRET.getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO user_mfa_totp (user_id, secret_enc, status, algorithm,
                                           digits, period_seconds, activated_at, key_version)
                VALUES (?, ?, 'ACTIVE', 'SHA1', 6, 30, now(), ?)
                """, userId, encryptedSecret, piiCipher.currentWriteKeyVersion());

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        URI base = URI.create("http://localhost:" + port);

        HttpResponse<String> wrongPassword = postJson(client, cookies, base, "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"Wrong-Password-1\",\"rememberMe\":true}");
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT failed_login_count FROM app_user WHERE id = ?",
                Integer.class, userId)).isEqualTo(1);

        HttpResponse<String> login = postJson(client, cookies, base, "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"rememberMe\":true}");
        assertThat(login.body()).contains("\"mfaRequired\":true");

        String valid = TotpCodeVerifier.now("SHA1", 6, TOTP_SECRET, clock.instant().getEpochSecond() / 30);
        String wrong = valid.equals("000000") ? "111111" : "000000";
        HttpResponse<String> wrongTotp = postJson(client, cookies, base, "/api/v1/auth/totp/verify",
                "{\"code\":\"" + wrong + "\"}");
        assertThat(wrongTotp.statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT failed_count FROM user_mfa_totp WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);

        HttpResponse<String> session = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/auth/session"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(session.body()).contains("\"authenticated\":false");
    }

    private static HttpResponse<String> postJson(HttpClient client, CookieManager cookies, URI base,
                                                 String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken(client, cookies, base))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfToken(HttpClient client, CookieManager cookies, URI base) throws Exception {
        HttpResponse<Void> response = client.send(HttpRequest.newBuilder(base.resolve("/api/v1/auth/csrf"))
                .GET().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(204);
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
    }
}
