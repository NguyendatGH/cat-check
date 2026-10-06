package com.catcheck.shared.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bo loc PII cua K1/L73 — p16 §16.6.3 (bang "TUYET DOI khong duoc log") + §16.6.7 (bo
 * {@code beforeSend}).
 *
 * <p>Day la <b>test tuan thu</b>: p16 §16.6.3 goi bang do la "hop dong tuan thu, khong phai
 * khuyen nghi" va noi ro vi pham la su co du lieu ca nhan theo p15. Moi assertion duoi day
 * tuong ung mot dong cua bang do.</p>
 */
class TelemetryScrubberTest {

    @Test
    @DisplayName("§16.6.3 dong 1 — email day du bi xoa khoi van ban tu do")
    void removesFullEmailAddresses() {
        String out = TelemetryScrubber.scrubText(
                "TypeError khi gui form cho nguyen.van.a@gmail.com va b_c@sub.domain.vn", 0);

        assertThat(out).doesNotContain("nguyen.van.a@gmail.com").doesNotContain("b_c@sub.domain.vn");
        assertThat(out).doesNotContain("@gmail.com").doesNotContain("@sub.domain.vn");
        assertThat(out).contains(TelemetryScrubber.REDACTED);
        // Phan khong phai PII phai con lai, neu khong thi log vo dung.
        assertThat(out).contains("TypeError");
    }

    @Test
    @DisplayName("§16.6.3 dong 3 — day 6 chu so (OTP/TOTP) bi xoa, so dai hon KHONG bi cat doi")
    void removesSixDigitCodesButKeepsLongerNumbers() {
        assertThat(TelemetryScrubber.scrubText("ma xac thuc 483912 het han", 0))
                .doesNotContain("483912");
        // 13 chu so = epoch millis trong mot stack trace; cat doi se tao ra rac vo nghia.
        assertThat(TelemetryScrubber.scrubText("ts=1760000000000 ok", 0))
                .contains("1760000000000");
    }

    @Test
    @DisplayName("§16.6.3 dong 9 — so dien thoai Viet Nam bi xoa o ca hai dang")
    void removesVietnamesePhoneNumbers() {
        String out = TelemetryScrubber.scrubText("lien he 0912345678 hoac +84912345678", 0);

        assertThat(out).doesNotContain("0912345678").doesNotContain("+84912345678");
    }

    @Test
    @DisplayName("§16.6.3 dong 5+7 — chuoi base64/token dai va data: URI bi xoa")
    void removesLongOpaqueBlobsAndInlineData() {
        // JWT: tung doan ngan hon nguong 40 ky tu nen chi LONG_OPAQUE_BLOB thi khong bat duoc —
        // can pattern neo vao tien to `eyJ` (xem javadoc JWT_LIKE).
        String jwtLike = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0";
        assertThat(TelemetryScrubber.scrubText("Authorization bearer " + jwtLike, 0))
                .doesNotContain(jwtLike)
                .doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");

        // Ten lop day du trong stack trace PHAI con lai — no dai hon 40 ky tu nhung co dau cham,
        // va che no di se pha huy chinh thu khien stack trace co ich.
        assertThat(TelemetryScrubber.scrubText(
                "at com.catcheck.scan.application.SubmitScanService.submit(SubmitScanService.java:88)", 0))
                .contains("com.catcheck.scan.application.SubmitScanService");

        assertThat(TelemetryScrubber.scrubText(
                "img src data:image/png;base64,iVBORw0KGgoAAAANSUhEUg", 0))
                .doesNotContain("iVBORw0KGgoAAAANSUhEUg");
    }

    @Test
    @DisplayName("§16.6.7 — cap key=value nhay cam bi che, giu lai TEN key")
    void masksSensitiveKeyValuePairsKeepingTheKeyName() {
        String out = TelemetryScrubber.scrubText(
                "submit failed password=Hunter2! otp=112233 sessionId=abc123def", 0);

        assertThat(out).doesNotContain("Hunter2!").doesNotContain("112233").doesNotContain("abc123def");
        // Giu ten key: "co mot mat khau o day va no da bi loc" la thong tin huu ich cho nguoi
        // debug; gia tri thi khong.
        assertThat(out).contains("password=").contains("otp=").contains("sessionId=");
    }

    @Test
    @DisplayName("§16.6.7 — scrubUri bo HOAN TOAN query string va fragment")
    void scrubUriDropsQueryStringEntirely() {
        assertThat(TelemetryScrubber.scrubUri(
                "https://catcheck.vn/auth/reset-password?token=SUPERSECRETVALUE&email=a@b.com"))
                .isEqualTo("https://catcheck.vn/auth/reset-password");

        assertThat(TelemetryScrubber.scrubUri("/scan?catId=11111111-2222-3333-4444-555555555555"))
                .isEqualTo("/scan");

        assertThat(TelemetryScrubber.scrubUri("/history#q=0912345678")).isEqualTo("/history");
    }

    @Test
    @DisplayName("Ky tu dieu khien bi thay bang dau cach — mot dong log phai la MOT dong")
    void collapsesControlCharactersIntoSpaces() {
        String out = TelemetryScrubber.scrubText("dong 1\nat foo\r\n\tat bar[31m", 0);

        assertThat(out).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t")
                .doesNotContain("");
        assertThat(out).contains("at foo").contains("at bar");
    }

    @Test
    @DisplayName("Tran do dai duoc ap dung sau khi loc")
    void truncatesAfterScrubbing() {
        // Dung chuoi NHIEU TU ngan: mot chuoi 100 ky tu lien se khop LONG_OPAQUE_BLOB va bi che
        // het truoc khi den buoc cat — tuc la test se khong kiem duoc dieu no muon kiem.
        String longText = "at foo ".repeat(30);

        assertThat(TelemetryScrubber.scrubText(longText, 10)).hasSize(11).endsWith("…");
    }

    @Test
    @DisplayName("Dau vao rong tra null, khong tra chuoi rong")
    void returnsNullForBlankInput() {
        assertThat(TelemetryScrubber.scrubText(null, 0)).isNull();
        assertThat(TelemetryScrubber.scrubText("   ", 0)).isNull();
        assertThat(TelemetryScrubber.scrubUri(null)).isNull();
        assertThat(TelemetryScrubber.scrubToken("  ", 10)).isNull();
    }

    @Test
    @DisplayName("scrubOrigin rut URI ve scheme://host — khong giu duong dan day du")
    void scrubOriginKeepsOnlySchemeAndHost() {
        assertThat(TelemetryScrubber.scrubOrigin("https://evil.example.com/a/b/c?u=123456"))
                .isEqualTo("https://evil.example.com");
        // Gia tri tu khoa cua CSP di nguyen.
        assertThat(TelemetryScrubber.scrubOrigin("inline")).isEqualTo("inline");
        assertThat(TelemetryScrubber.scrubOrigin("eval")).isEqualTo("eval");
    }

    @Test
    @DisplayName("scrubToken bo moi ky tu ngoai tap ky thuat — chan nhoi du lieu vao truong enum")
    void scrubTokenStripsUnexpectedCharacters() {
        assertThat(TelemetryScrubber.scrubToken("script-src 'self'", 64))
                .isEqualTo("script-src self");
        assertThat(TelemetryScrubber.scrubToken("1.2.3-rc.1", 64)).isEqualTo("1.2.3-rc.1");
    }
}
