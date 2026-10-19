package com.catcheck.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer("http://localhost:5173");

    @Test
    void otpTemplateUsesTitleAsSubjectAndFillsTheCode() {
        var rendered = renderer.render("AUTH_OTP_REGISTER", "vi",
                Map.<String, Object>of("code", "482901", "expiresInMinutes", "5")).orElseThrow();

        assertThat(rendered.subject()).isEqualTo("Mã xác thực đăng ký CatCheck");
        assertThat(rendered.html()).contains("482901").contains("5 phút");
        assertThat(rendered.html()).doesNotContain("{{");
    }

    @Test
    void variableValuesAreEscapedInTheBodyButNotInTheSubject() {
        var rendered = renderer.render("REMINDER_SCAN_DUE", "vi",
                Map.<String, Object>of("catName", "<script>alert(1)</script> & Mun",
                        "deepLink", "/scan?catId=abc")).orElseThrow();

        assertThat(rendered.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Mun");
        assertThat(rendered.html()).doesNotContain("<script>alert(1)");
        assertThat(rendered.subject()).isEqualTo("Đến lịch quét cho <script>alert(1)</script> & Mun");
    }

    @Test
    void reminderLinkIsAbsoluteAndUsesTheConfiguredAppUrl() {
        var rendered = renderer.render("REMINDER_SCAN_DUE", "vi",
                Map.<String, Object>of("catName", "Mun", "deepLink", "/scan?catId=abc")).orElseThrow();

        assertThat(rendered.html()).contains("href=\"http://localhost:5173/scan?catId=abc\"");
    }

    @Test
    void newlinesInsideSubjectValuesAreRemovedSoHeadersCannotBeInjected() {
        var rendered = renderer.render("REMINDER_SCAN_DUE", "vi",
                Map.<String, Object>of("catName", "Mun\r\nBcc: attacker@example.com",
                        "deepLink", "/scan")).orElseThrow();

        assertThat(rendered.subject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void missingVariablesBecomeEmptyInsteadOfLeakingPlaceholders() {
        var rendered = renderer.render("AUTH_OTP_REGISTER", "vi", Map.of()).orElseThrow();

        assertThat(rendered.html()).doesNotContain("{{");
    }

    @Test
    void sameTemplateAndVariablesRenderInTheRequestedLocale() {
        Map<String, Object> variables = Map.of("code", "482901", "expiresInMinutes", "5");

        var vi = renderer.render("AUTH_OTP_REGISTER", "vi", variables).orElseThrow();
        var en = renderer.render("AUTH_OTP_REGISTER", "en", variables).orElseThrow();

        assertThat(vi.subject()).isEqualTo("Mã xác thực đăng ký CatCheck");
        assertThat(vi.html()).contains("<html lang=\"vi\">").contains("5 phút");
        assertThat(en.subject()).isEqualTo("CatCheck registration verification code");
        assertThat(en.html()).contains("<html lang=\"en\">").contains("482901").contains("5 minutes");
        assertThat(en.html()).doesNotContain("phút").doesNotContain("{{");
    }

    @Test
    void everyTemplateHasAnEnglishVariantWithItsOwnSubject() {
        Map<String, Object> variables = Map.of("catName", "Mun", "deepLink", "/scan");

        for (String code : new String[] {"AUTH_OTP_REGISTER", "AUTH_OTP_RESET_PASSWORD", "REMINDER_SCAN_DUE"}) {
            var vi = renderer.render(code, "vi", variables).orElseThrow();
            var en = renderer.render(code, "en", variables).orElseThrow();
            assertThat(en.subject()).isNotEqualTo(vi.subject());
            assertThat(en.html()).contains("<html lang=\"en\">");
        }
    }

    @Test
    void unknownOrMissingLocaleFallsBackToVietnamese() {
        for (String locale : new String[] {"fr", "../vi", "EN-US", "", null}) {
            var rendered = renderer.render("AUTH_OTP_REGISTER", locale, Map.of()).orElseThrow();
            assertThat(rendered.subject()).isEqualTo("Mã xác thực đăng ký CatCheck");
        }
    }

    @Test
    void unknownOrMalformedTemplateCodesRenderNothing() {
        assertThat(renderer.render("EMAIL_OTP", "vi", Map.of())).isEmpty();
        assertThat(renderer.render("../application", "vi", Map.of())).isEmpty();
        assertThat(renderer.render(null, "vi", Map.of())).isEmpty();
    }
}
