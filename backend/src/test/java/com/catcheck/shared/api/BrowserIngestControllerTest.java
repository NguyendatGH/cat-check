package com.catcheck.shared.api;

import com.catcheck.shared.api.dto.ClientErrorRequest;
import com.catcheck.shared.api.dto.CspReportRequest;
import com.catcheck.shared.application.spi.PublicIngressRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * K1 {@code POST /csp-report} va L73 {@code POST /client-errors}.
 *
 * <p><b>Test quan trong nhat o day khong phai ma HTTP ma la NOI DUNG DONG LOG.</b> Hai endpoint
 * nay cong khai va chung ghi thang vao file log JSON cua backend; p16 §16.6.7 canh bao neu khong
 * loc thi "telemetry tro thanh kenh ro du lieu lon nhat cua san pham", va CLAUDE.md dat "khong log
 * PII" vao muc hard rule. Vi vay test gan mot {@code ListAppender} vao dung hai logger ma controller
 * dung, roi <b>doc lai chuoi da format</b> va khang dinh gia tri goc khong con o do. Kiem bang
 * mat tren log that khong the lap lai duoc o CI; kiem bang appender thi duoc.</p>
 */
class BrowserIngestControllerTest {

    /** Han muc that cua p8 — khong phai so ngau nhien cho test. */
    private static final int CSP_LIMIT_PER_MINUTE = 60;
    private static final int CLIENT_ERROR_LIMIT_PER_MINUTE = 10;

    private final List<Logger> attachedLoggers = new ArrayList<>();
    private ListAppender<ILoggingEvent> cspAppender;
    private ListAppender<ILoggingEvent> clientErrorAppender;

    @BeforeEach
    void attachAppenders() {
        cspAppender = attach("catcheck.telemetry.csp");
        clientErrorAppender = attach("catcheck.telemetry.client-error");
    }

    @AfterEach
    void detachAppenders() {
        attachedLoggers.forEach(logger -> logger.detachAndStopAllAppenders());
        attachedLoggers.clear();
    }

    private ListAppender<ILoggingEvent> attach(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        attachedLoggers.add(logger);
        return appender;
    }

    /** Toan bo noi dung da format cua moi dong log — dung chinh thu se nam trong file log. */
    private static String rendered(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + '\n' + b);
    }

    private static final tools.jackson.databind.json.JsonMapper JSON =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private static BrowserIngestController controller() {
        return new BrowserIngestController(new AllowAllRateLimiter(), JSON);
    }

    private static BrowserIngestController realLimitController() {
        return new BrowserIngestController(
                new com.catcheck.shared.infrastructure.ratelimit.Bucket4jPublicIngressRateLimiter(),
                JSON);
    }

    /**
     * {@code setContent} chu khong {@code addHeader("Content-Length", ...)}:
     * {@code MockHttpServletRequest.getContentLengthLong()} doc do dai cua MANG BYTE da dat, chu
     * khong doc header — dat header se cho mot test xanh ma thuc te khong kiem gi.
     */
    private static MockHttpServletRequest request(String ip, int contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        if (contentLength >= 0) {
            request.setContentType("application/json");
            request.setContent(new byte[contentLength]);
        }
        return request;
    }

    // ----------------------------------------------------------------- L73

    @Test
    @DisplayName("L73 tra 202 KHONG body (p8 §8.4.12)")
    void clientErrorReturns202WithoutBody() {
        ResponseEntity<Void> response = controller().receiveClientError(
                new ClientErrorRequest("Boom", "at a()", "1.2.3", "trace-1", "/scan"),
                request("10.0.0.1", 100));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("L73 KHONG ghi query string vao log (p16 §16.6.7: 'xoa moi gia tri input, URL query')")
    void clientErrorNeverLogsQueryString() {
        controller().receiveClientError(new ClientErrorRequest(
                        "Khong tai duoc chunk",
                        "at loadChunk (/assets/app.js:1:1)",
                        "1.4.0",
                        "trace-xyz",
                        // Client gui ca query string — duong nay PHAI bi cat.
                        "/auth/reset-password?token=SUPERSECRETTOKENVALUE&email=victim@gmail.com"),
                request("10.0.0.2", 300));

        String log = rendered(clientErrorAppender);
        assertThat(log).as("duong dan phai con lai").contains("/auth/reset-password");
        assertThat(log).as("query string phai bi cat hoan toan")
                .doesNotContain("?")
                .doesNotContain("token=SUPERSECRETTOKENVALUE")
                .doesNotContain("SUPERSECRETTOKENVALUE")
                .doesNotContain("victim@gmail.com");
    }

    @Test
    @DisplayName("L73 KHONG ghi gia tri input / noi dung form vao log")
    void clientErrorNeverLogsFormValuesOrInputs() {
        controller().receiveClientError(new ClientErrorRequest(
                        // Mot message do frontend tu ghep, co ca gia tri nguoi dung vua go.
                        "Validation failed: email=nguyen.van.a@gmail.com password=Hunter2! otp=483912 "
                                + "phone=0912345678",
                        "at submitForm (RegisterPage.tsx:42) value=0912345678",
                        "1.4.0", "trace-abc", "/auth/register"),
                request("10.0.0.3", 500));

        String log = rendered(clientErrorAppender);
        assertThat(log)
                .doesNotContain("nguyen.van.a@gmail.com")
                .doesNotContain("Hunter2!")
                .doesNotContain("483912")
                .doesNotContain("0912345678");
        // Phan chan doan phai con, neu khong thi endpoint vo dung.
        assertThat(log).contains("RegisterPage.tsx:42").contains("trace-abc").contains("1.4.0");
    }

    @Test
    @DisplayName("L73 KHONG the nhan truong ngoai 5 truong p8 — Jackson bo o tang parse")
    void clientErrorRecordIgnoresUnknownJsonFields() throws Exception {
        // Khong goi controller: chung minh chinh DTO khong co cho de du lieu la di vao.
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        ClientErrorRequest parsed = mapper.readValue("""
                {"message":"Boom","stack":"at a()","appVersion":"1.0.0","traceId":"t","route":"/x",
                 "formData":{"password":"Hunter2!"},"queryString":"?otp=483912",
                 "breadcrumbs":[{"value":"nguyen.van.a@gmail.com"}]}
                """, ClientErrorRequest.class);

        assertThat(parsed.message()).isEqualTo("Boom");
        // Ba truong la khong ton tai tren record — day la bang chung manh nhat cho cau "tuyet doi
        // khong nhan gia tri input/query string/noi dung form": khong co truong thi khong the log.
        assertThat(ClientErrorRequest.class.getRecordComponents()).hasSize(5);
        assertThat(List.of(ClientErrorRequest.class.getRecordComponents())
                .stream().map(c -> c.getName()).toList())
                .containsExactly("message", "stack", "appVersion", "traceId", "route");
    }

    @Test
    @DisplayName("L73 body > 8 KB tra 413 va KHONG ghi log (p8 L73)")
    void clientErrorRejectsOversizedBody() {
        ResponseEntity<Void> response = controller().receiveClientError(
                new ClientErrorRequest("x", "y", "1", "t", "/a"),
                request("10.0.0.4", BrowserIngestController.MAX_CLIENT_ERROR_BYTES + 1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(clientErrorAppender.list).isEmpty();
    }

    @Test
    @DisplayName("L73 truong dai hon khai bao bi CAT, khong lam ca bao cao bi tu choi")
    void clientErrorTruncatesOverlongFieldsInsteadOfRejecting() {
        // Bug that da sua: tham so truoc day mang @Valid, nen mot `message` dai hon @Size(max)
        // tra 400 VALIDATION_FAILED — bean validation chay khi PHAN GIAI THAM SO, truoc dong dau
        // tien cua method, nen buoc kiem 8 KB khong bao gio duoc toi. p8 L73 chot 413 cho "than
        // qua lon" va 202 cho moi truong hop con lai.
        ResponseEntity<Void> response = controller().receiveClientError(
                new ClientErrorRequest("Boom ".repeat(1_000), null, "1.0.0", "t-1", "/a"),
                request("10.0.0.5", 6_000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String log = rendered(clientErrorAppender);
        assertThat(log).contains("client_error").contains("t-1");
        assertThat(log).as("message bi cat o tran 1000 ky tu, khong ghi ca 5000")
                .hasSizeLessThan(2_500);
    }

    @Test
    @DisplayName("L73 rate limit 10/phut/IP — lan thu 11 tra 429 kem Retry-After (p8 §8.3.5(d))")
    void clientErrorEnforcesTenPerMinutePerIp() {
        BrowserIngestController controller = realLimitController();
        ClientErrorRequest body = new ClientErrorRequest("Boom", null, "1.0.0", "t", "/a");

        for (int i = 1; i <= CLIENT_ERROR_LIMIT_PER_MINUTE; i++) {
            assertThat(controller.receiveClientError(body, request("203.0.113.7", 100)).getStatusCode())
                    .as("request thu " + i + " trong han muc")
                    .isEqualTo(HttpStatus.ACCEPTED);
        }

        ResponseEntity<Void> blocked =
                controller.receiveClientError(body, request("203.0.113.7", 100));
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .as("p8 §8.3.5(d) + p16 NFR-40: Retry-After bat buoc 100%")
                .isNotNull();
        assertThat(Long.parseLong(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .isGreaterThanOrEqualTo(1);

        // Han muc theo IP: mot IP khac KHONG bi chan lay.
        assertThat(controller.receiveClientError(body, request("203.0.113.8", 100)).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
    }

    // ------------------------------------------------------------------ K1

    @Test
    @DisplayName("K1 LUON tra 204 — ke ca than rong, than hong, hay vuot han muc (p8 K1)")
    void cspReportAlwaysReturns204() {
        BrowserIngestController controller = realLimitController();

        // Than null / rong / JSON hong / thieu object boc ngoai: tat ca phai la 204, khong 400.
        // p8 K1 noi "luon 204"; trinh duyet khong doc ma loi cua endpoint nay va khong thu lai.
        for (String badBody : new String[] {null, "", "   ", "{khong-phai-json", "{}", "[]"}) {
            assertThat(controller.receiveCspReport(badBody, request("198.51.100.1", 10))
                    .getStatusCode())
                    .as("than = %s", badBody)
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }

        String report = cspJson("https://catcheck.vn/scan", null, "https://evil.example.com/x", null);
        for (int i = 0; i < CSP_LIMIT_PER_MINUTE + 5; i++) {
            assertThat(controller.receiveCspReport(report, request("198.51.100.2", 200)).getStatusCode())
                    .as("bao cao thu " + i + " — p8 K1: 'luon 204, ke ca khi vuot han muc'")
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }
        // Han muc VAN duoc ap dung: so dong `csp_violation` phai bang han muc, khong phai bang
        // so request. Loc theo noi dung chu khong dem ca list: nhanh "than hong" o tren cung ghi
        // mot dong DEBUG, va dem ca chung se lam phep khang dinh nay do vi mot ly do khong lien
        // quan den rate limit.
        assertThat(cspAppender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("csp_violation"))
                .count())
                .isEqualTo(CSP_LIMIT_PER_MINUTE);
    }

    @Test
    @DisplayName("K1 doc duoc Content-Type application/csp-report cua dang report-uri cu")
    void cspReportAcceptsTheLegacyContentType() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("198.51.100.9");
        httpRequest.setContentType("application/csp-report;charset=UTF-8");

        assertThat(controller().receiveCspReport(
                cspJson("https://catcheck.vn/", null, "inline", null), httpRequest).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        // Bug that: voi @RequestBody CspReportRequest + consumes, media type nay tra 500
        // (HttpMediaTypeNotSupportedException) vi converter Jackson chi nhan json / *+json.
        assertThat(rendered(cspAppender)).as("bao cao PHAI duoc ghi, khong bi 500 truoc do")
                .contains("csp_violation");
    }

    @Test
    @DisplayName("K1 KHONG ghi query string, referrer hay original-policy vao log")
    void cspReportNeverLogsQueryStringOrReferrer() {
        controller().receiveCspReport("""
                {"csp-report":{
                  "document-uri":"https://catcheck.vn/auth/reset-password?token=SECRETTOKEN123456789&email=a@b.com",
                  "referrer":"https://catcheck.vn/previous?phone=0912345678",
                  "violated-directive":"script-src 'self'",
                  "effective-directive":"script-src",
                  "original-policy":"default-src 'self'; script-src 'self'",
                  "blocked-uri":"https://evil.example.com/steal?sessionId=abcdef",
                  "disposition":"report","status-code":200,
                  "source-file":"https://catcheck.vn/assets/app.js?v=483912",
                  "line-number":10,"column-number":5,
                  "sample":"const password = 'Hunter2!'"}}
                """, request("198.51.100.3", 400));

        String log = rendered(cspAppender);
        assertThat(log).contains("https://catcheck.vn/auth/reset-password");
        assertThat(log)
                .doesNotContain("SECRETTOKEN123456789")
                .doesNotContain("a@b.com")
                // referrer KHONG duoc log (URL day du cua trang truoc do)
                .doesNotContain("previous")
                .doesNotContain("0912345678")
                // blocked-uri rut ve origin: duong dan + query cua resource bi chan khong duoc log
                .doesNotContain("/steal")
                .doesNotContain("abcdef")
                .doesNotContain("483912")
                // `sample` = doan ma nguon quanh cho vi pham, tuc la NOI DUNG TRANG.
                .doesNotContain("Hunter2!");
        assertThat(log).as("origin bi chan phai con, do la thong tin van hanh can thiet")
                .contains("https://evil.example.com");
    }

    @Test
    @DisplayName("K1 KHONG the nhan truong 'sample' (doan ma nguon quanh cho vi pham)")
    void cspReportIgnoresSampleField() {
        var components = List.of(CspReportRequest.Report.class.getRecordComponents())
                .stream().map(c -> c.getName()).toList();

        // Reporting API moi gui ca `sample` = NOI DUNG TRANG. Khong co truong do tren record
        // nghia la no bi bo ngay o tang parse.
        assertThat(components).doesNotContain("sample");
    }

    @Test
    @DisplayName("K1 than qua lon bi bo qua trong im lang, van 204")
    void cspReportIgnoresOversizedBody() {
        String huge = "{\"csp-report\":{\"document-uri\":\""
                + "a".repeat(BrowserIngestController.MAX_CSP_REPORT_BYTES) + "\"}}";

        assertThat(controller().receiveCspReport(huge, request("198.51.100.4", 10)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rendered(cspAppender)).doesNotContain("csp_violation");
    }

    /** Than bao cao CSP dang JSON — dung cai trinh duyet gui, khong phai mot object Java. */
    private static String cspJson(String documentUri, String referrer, String blockedUri, String sample) {
        return """
                {"csp-report":{"document-uri":%s,"referrer":%s,"violated-directive":"script-src 'self'",
                 "effective-directive":"script-src","blocked-uri":%s,"disposition":"enforce",
                 "status-code":200,"line-number":1,"column-number":1,"sample":%s}}
                """.formatted(quote(documentUri), quote(referrer), quote(blockedUri), quote(sample));
    }

    private static String quote(String value) {
        return value == null ? "null" : '"' + value.replace("\"", "\\\"") + '"';
    }

    /** Cho nhung test khong kiem rate limit — de gioi han khong lam nhieu phep khang dinh khac. */
    private static final class AllowAllRateLimiter implements PublicIngressRateLimiter {

        @Override
        public boolean tryConsume(Limit limit, String clientIp) {
            return true;
        }

        @Override
        public long secondsUntilRefill(Limit limit, String clientIp) {
            return 0;
        }
    }

    @Test
    @DisplayName("Han muc khai bao trong enum khop dung con so cua p8")
    void declaredLimitsMatchSpec() {
        Map<PublicIngressRateLimiter.Limit, Integer> expected = Map.of(
                PublicIngressRateLimiter.Limit.CSP_REPORT, CSP_LIMIT_PER_MINUTE,
                PublicIngressRateLimiter.Limit.CLIENT_ERROR, CLIENT_ERROR_LIMIT_PER_MINUTE);

        expected.forEach((limit, perMinute) ->
                assertThat(limit.perMinute()).as(limit.name()).isEqualTo(perMinute));
    }
}
