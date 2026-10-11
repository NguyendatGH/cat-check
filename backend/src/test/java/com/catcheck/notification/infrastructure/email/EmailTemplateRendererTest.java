package com.catcheck.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer("http://localhost:5173");

    @Test
    void otpTemplateUsesTitleAsSubjectAndFillsTheCode() {
        var rendered = renderer.render("AUTH_OTP_REGISTER",
                Map.<String, Object>of("code", "482901", "expiresInMinutes", "5")).orElseThrow();

        assertThat(rendered.subject()).isEqualTo("Mã xác thực đăng ký CatCheck");
        assertThat(rendered.html()).contains("482901").contains("5 phút");
        assertThat(rendered.html()).doesNotContain("{{");
    }

    @Test
    void variableValuesAreEscapedInTheBodyButNotInTheSubject() {
        var rendered = renderer.render("REMINDER_SCAN_DUE",
                Map.<String, Object>of("catName", "<script>alert(1)</script> & Mun",
                        "deepLink", "/scan?catId=abc")).orElseThrow();

        assertThat(rendered.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Mun");
        assertThat(rendered.html()).doesNotContain("<script>alert(1)");
        assertThat(rendered.subject()).isEqualTo("Đến lịch quét cho <script>alert(1)</script> & Mun");
    }

    @Test
    void reminderLinkIsAbsoluteAndUsesTheConfiguredAppUrl() {
        var rendered = renderer.render("REMINDER_SCAN_DUE",
                Map.<String, Object>of("catName", "Mun", "deepLink", "/scan?catId=abc")).orElseThrow();

        assertThat(rendered.html()).contains("href=\"http://localhost:5173/scan?catId=abc\"");
    }

    @Test
    void newlinesInsideSubjectValuesAreRemovedSoHeadersCannotBeInjected() {
        var rendered = renderer.render("REMINDER_SCAN_DUE",
                Map.<String, Object>of("catName", "Mun\r\nBcc: attacker@example.com",
                        "deepLink", "/scan")).orElseThrow();

        assertThat(rendered.subject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void missingVariablesBecomeEmptyInsteadOfLeakingPlaceholders() {
        var rendered = renderer.render("AUTH_OTP_REGISTER", Map.of()).orElseThrow();

        assertThat(rendered.html()).doesNotContain("{{");
    }

    @Test
    void unknownOrMalformedTemplateCodesRenderNothing() {
        assertThat(renderer.render("EMAIL_OTP", Map.of())).isEmpty();
        assertThat(renderer.render("../application", Map.of())).isEmpty();
        assertThat(renderer.render(null, Map.of())).isEmpty();
    }
}
