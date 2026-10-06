package com.catcheck.shared.api;

import com.catcheck.shared.api.dto.ClientErrorRequest;
import com.catcheck.shared.api.dto.CspReportRequest;
import com.catcheck.shared.application.TelemetryScrubber;
import com.catcheck.shared.application.spi.PublicIngressRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Hai endpoint <b>cong khai</b> nhan du lieu do trinh duyet gui: K1
 * {@code POST /api/v1/csp-report} (p8 §8.4.11) va L73 {@code POST /api/v1/client-errors}
 * (p8 §8.4.12 muc (f)).
 *
 * <p><b>Mot controller cho ca hai</b> vi chung chia het moi rang buoc khong tam thuong — cong
 * khai, mien CSRF, rate limit theo IP, tran kich thuoc than, va <b>bat buoc loc PII truoc khi
 * ghi log</b>. Tach doi se nhan doi bon quy tac do o hai noi, va mot ban se troi.</p>
 *
 * <p><b>Nam o module {@code shared}, khong o {@code admin}:</b> toan bo {@code /api/v1/admin/**}
 * bi chan boi {@code AdminMfaGateFilter} + luat role, nen dat mot endpoint cong khai vao module
 * {@code admin} hoac la bi chan, hoac phai khoet lo trong luat phan quyen. Cung ly do da dua
 * K2 ve {@code shared.api.PublicSystemStatusController}.</p>
 *
 * <p><b>Thay cho Sentry o MVP</b> ({@code 03-arbitration.md} D-C1, p16 §16.6.7): ca hai chi ghi
 * vao <b>cung mot file log JSON cua backend</b>, khong gui du lieu ra ngoai bien gioi. Nho
 * {@code traceId}, mot stack trace cua frontend noi duoc voi dong log server tuong ung du hai
 * dau khong chia se tien trinh.</p>
 *
 * <p><b>Chua luu DB.</b> p11 §11.8.3 muon bao cao CSP "luu toi da 30 ngay, khong luu IP day
 * du", nhung p4 §4.9.2 khong co bang {@code csp_report} va khong co khe migration nao cho no —
 * handoff H15.183. Hien tai bao cao di vao log ung dung, noi da co san retention 30 ngay theo
 * p16 §16.6.6, nen yeu cau ve thoi han van dung; cai thieu la kha nang truy van.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Telemetry", description = "K1 + L73 — bao cao CSP va loi runtime cua frontend")
public class BrowserIngestController {

    /**
     * Logger rieng theo tung endpoint, khong dung logger cua class: nguoi van hanh loc
     * {@code catcheck.telemetry.csp} va {@code catcheck.telemetry.client-error} nhu hai luong
     * doc lap, va {@code logging.level} dieu chinh duoc tung luong (vi du ha CSP xuong
     * {@code WARN} trong tuan dau bat {@code Report-Only}, luc luong bao cao lon nhat).
     */
    private static final Logger CSP_LOG = LoggerFactory.getLogger("catcheck.telemetry.csp");
    private static final Logger CLIENT_ERROR_LOG = LoggerFactory.getLogger("catcheck.telemetry.client-error");

    /** p8 L73: "body ≤ 8 KB". Tinh theo byte vi gioi han la cua duong truyen, khong phai ky tu. */
    static final int MAX_CLIENT_ERROR_BYTES = 8 * 1024;

    /** Bao cao CSP do trinh duyet sinh nen nho; 16 KB du cho ca {@code original-policy}. */
    static final int MAX_CSP_REPORT_BYTES = 16 * 1024;

    private final PublicIngressRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;

    public BrowserIngestController(PublicIngressRateLimiter rateLimiter, JsonMapper jsonMapper) {
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
    }

    // ------------------------------------------------------------------- K1

    /**
     * p8 K1 chot hai dieu bat thuong, va chung co ly do:
     * <ul>
     *   <li><b>"Luon 204, ke ca khi vuot han muc"</b> — khong tra {@code 429}. Trinh duyet
     *       khong doc {@code Retry-After} cho bao cao CSP va khong thu lai; mot {@code 429} chi
     *       sinh them tieng on o console cua nguoi dung ma khong thay doi gi. Han muc 60/phut/IP
     *       van duoc ap dung, chi la no bieu hien thanh "bo qua" chu khong thanh ma loi.</li>
     *   <li><b>Nhan MOI content type</b> — {@code application/csp-report} (dang
     *       {@code report-uri} cu ma Chrome/Safari van gui), {@code application/reports+json}
     *       (Reporting API moi) va {@code application/json}. Chan mot trong so do nghia la mot ho
     *       trinh duyet im lang khong bao cao duoc gi, va mot CSP sai se khong bao gio bi phat
     *       hien. Cach dat duoc dieu do — nhan {@code String} roi tu parse — duoc giai thich o
     *       {@link #parseCspReport}, va no xuat phat tu mot bug that da do duoc.</li>
     * </ul>
     */
    @Operation(
            operationId = "postCspReport",
            summary = "K1 — nhan bao cao vi pham CSP",
            description = """
                    Cong khai, khong can phien, mien CSRF (bao cao do chinh trinh duyet gui). \
                    LUON tra 204 — ke ca khi vuot han muc 60/phut/IP, khi than khong doc duoc, \
                    hay khi thieu truong. Noi dung duoc loc PII (bo query string, email, ma 6 so, \
                    chuoi base64 dai) truoc khi ghi log; IP day du KHONG duoc ghi.""")
    @PostMapping(path = "/csp-report")
    public ResponseEntity<Void> receiveCspReport(
            @RequestBody(required = false) String rawBody,
            HttpServletRequest httpRequest) {
        if (!rateLimiter.tryConsume(PublicIngressRateLimiter.Limit.CSP_REPORT, clientIp(httpRequest))
                || rawBody == null || rawBody.isBlank()
                || rawBody.length() > MAX_CSP_REPORT_BYTES) {
            return ResponseEntity.noContent().build();
        }
        CspReportRequest body = parseCspReport(rawBody);
        if (body == null || body.cspReport() == null) {
            return ResponseEntity.noContent().build();
        }
        CspReportRequest.Report report = body.cspReport();
        // `original-policy` va `referrer` CO Y khong duoc log: policy la hang so da biet (p11
        // §11.8.3) nen khong them thong tin, con referrer la URL day du cua trang truoc do —
        // dung loai du lieu ma p16 §16.6.7 doi phai xoa.
        CSP_LOG.warn("csp_violation effectiveDirective={} violatedDirective={} documentPath={} "
                        + "blockedOrigin={} disposition={} statusCode={} sourcePath={} line={} column={}",
                TelemetryScrubber.scrubToken(report.effectiveDirective(), 64),
                TelemetryScrubber.scrubToken(report.violatedDirective(), 160),
                TelemetryScrubber.scrubUri(report.documentUri()),
                TelemetryScrubber.scrubOrigin(report.blockedUri()),
                TelemetryScrubber.scrubToken(report.disposition(), 16),
                report.statusCode(),
                TelemetryScrubber.scrubUri(report.sourceFile()),
                report.lineNumber(),
                report.columnNumber());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ L73

    /**
     * p8 L73. Khac K1 o dung mot cho: <b>vuot han muc thi tra {@code 429} that</b> kem
     * {@code Retry-After} (p8 §8.3.5(d) bat buoc 100%, p16 NFR-40), vi ben goi la
     * {@code telemetry.ts} cua chinh chung ta — no doc duoc header va biet phai lui lai.
     *
     * <p>{@code 202} khong body la hop dong cua p8. Khong tra id, khong tra echo: moi thu tra
     * ve se thanh mot kenh de do xem bao cao da duoc nhan hay chua, tren mot endpoint cong
     * khai.</p>
     *
     * <p><b>KHONG co {@code @Valid}, va day la mot sua loi co do:</b> ban dau tham so mang
     * {@code @Valid}, va mot than 9 KB tra <b>400 VALIDATION_FAILED</b> chu khong phai
     * {@code 413} — vi bean validation chay trong luc PHAN GIAI THAM SO, tuc la truoc dong dau
     * tien cua method, nen buoc kiem {@code Content-Length} ben duoi khong bao gio duoc toi. Do
     * that bang curl tren cong 8094. p8 L73 chot "body ≤ 8 KB" nen {@code 413} moi la cau tra loi
     * dung (p8 §8.1.12 danh rieng ma do cho "payload qua lon"), va client can phan biet "bao cao
     * qua dai, hay cat ngan" voi "bao cao sai dinh dang".
     *
     * <p>Cac {@code @Size} tren {@link ClientErrorRequest} vi vay la <b>tai lieu cho OpenAPI</b>,
     * khong phai cong chan. Rang buoc thuc su la hai lop deu nam trong method nay: tran 8 KB cho
     * ca than, va {@code TelemetryScrubber} cat tung truong truoc khi ghi log. Mot truong dai hon
     * khai bao se bi CAT, khong lam ca bao cao bi tu choi — dung tinh than cua p8 (tra {@code 202}
     * va ghi phan doc duoc), vi lan deploy lam vo bundle chinh la luc frontend it co kha nang gui
     * du va dung dinh dang nhat.</p>
     */
    @Operation(
            operationId = "postClientError",
            summary = "L73 — nhan loi runtime cua frontend",
            description = """
                    Cong khai (loi tai chunk xay ra TRUOC khi co phien), mien CSRF. \
                    Chi nhan {message, stack, appVersion, traceId, route} — moi truong khac bi \
                    bo ngay o tang parse, nen gia tri input/query string/noi dung form khong the \
                    vao he thong. Body ≤ 8 KB (vuot ⇒ 413), rate limit 10/phut/IP (vuot ⇒ 429 + \
                    Retry-After). Tra 202 khong body.""")
    @PostMapping(path = "/client-errors", consumes = "application/json")
    public ResponseEntity<Void> receiveClientError(
            @RequestBody ClientErrorRequest body,
            HttpServletRequest httpRequest) {
        if (httpRequest.getContentLengthLong() > MAX_CLIENT_ERROR_BYTES) {
            // 413 chu khong 400: p8 §8.1.12 danh rieng ma nay cho "payload qua lon", va client
            // can phan biet "bao cao qua dai, hay cat ngan" voi "bao cao sai dinh dang".
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        String clientIp = clientIp(httpRequest);
        if (!rateLimiter.tryConsume(PublicIngressRateLimiter.Limit.CLIENT_ERROR, clientIp)) {
            long retryAfter = Math.max(1,
                    rateLimiter.secondsUntilRefill(PublicIngressRateLimiter.Limit.CLIENT_ERROR, clientIp));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                    .build();
        }
        CLIENT_ERROR_LOG.error("client_error traceId={} appVersion={} route={} message={} stack={}",
                TelemetryScrubber.scrubToken(body.traceId(), 64),
                TelemetryScrubber.scrubToken(body.appVersion(), 64),
                TelemetryScrubber.scrubUri(body.route()),
                TelemetryScrubber.scrubText(body.message(), 1_000),
                TelemetryScrubber.scrubText(body.stack(), 4_000));
        return ResponseEntity.accepted().build();
    }

    /* ---------------------------------------------------------------- helper */

    /**
     * Doc than bao cao CSP bang {@link JsonMapper} <b>thay vi</b> de Spring binding lam.
     *
     * <p><b>Bug that da sua, do bang curl tren cong 8094:</b> ban dau method nay nhan
     * {@code @RequestBody CspReportRequest} voi
     * {@code consumes = {"application/csp-report", "application/reports+json", "application/json"}}.
     * Mapping KHOP, nhung request tra <b>500</b> kem
     * {@code HttpMediaTypeNotSupportedException: Content-Type 'application/csp-report;charset=UTF-8'
     * is not supported}: {@code consumes} chi quyet dinh mapping nao duoc chon, con viec doc than
     * can mot {@code HttpMessageConverter} <b>ho tro dung media type do</b> — va converter Jackson
     * chi nhan {@code application/json} + {@code application/*+json}.
     * {@code application/csp-report} khong khop mau nao trong hai mau do.
     *
     * <p>Hau qua that neu khong sua: bao cao vi pham tu <b>dang {@code report-uri} cu</b> — dang
     * ma Chrome/Safari hien van gui, va la dang p11 §11.8.3 chi dinh khi bat
     * {@code Report-Only} — bi 500 het, nen "chay it nhat 1 tuan tren staging + 1 tuan tren prod,
     * doc bao cao" cua p11 khong the thuc hien.
     *
     * <p><b>Nhan {@code String} roi tu parse giai quyet ca mot yeu cau khac cua p8 K1: "LUON
     * {@code 204}".</b> {@code StringHttpMessageConverter} nhan {@code *&#47;*} nen moi media type
     * deu doc duoc; va mot than JSON hong tro thanh {@code null} o day thay vi mot
     * {@code 400 MALFORMED_REQUEST} do Spring sinh ra truoc khi method duoc goi. Trinh duyet khong
     * doc ma loi cua endpoint nay va khong thu lai, nen {@code 400} chi la tieng on.</p>
     *
     * @return {@code null} neu than khong doc duoc — goi la "bo qua trong im lang", dung hop dong
     *         cua p8 K1
     */
    private CspReportRequest parseCspReport(String rawBody) {
        try {
            return jsonMapper.readValue(rawBody, CspReportRequest.class);
        } catch (RuntimeException ex) {
            // Khong log ca than: no den tu trinh duyet nguoi dung va chua qua bo loc PII nao.
            // Chi log lop ngoai le — du de biet "co bao cao gui sai dinh dang".
            CSP_LOG.debug("Bo qua bao cao CSP khong doc duoc: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * IP dung lam khoa rate limit. <b>Chi dung {@code getRemoteAddr()}</b>, khong doc
     * {@code X-Forwarded-For}: hai endpoint nay cong khai, nen mot header do client tu dien se
     * cho phep ke tan cong doi mot gia tri la thoat khoi moi gioi han — dung canh bao cua p11
     * §11.7.1. Khi dat sau Caddy, p18 cau hinh proxy ghi lai {@code remoteAddr} that.
     *
     * <p>Gia tri nay <b>khong bao gio duoc ghi log</b> (p11 §11.8.3: "khong luu IP day du";
     * p16 §16.6.3 dong cuoi) — no chi ton tai trong RAM lam khoa thung Bucket4j.</p>
     */
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
