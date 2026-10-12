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

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Telemetry", description = "K1 + L73 — bao cao CSP va loi runtime cua frontend")
public class BrowserIngestController {

    private static final Logger CSP_LOG = LoggerFactory.getLogger("catcheck.telemetry.csp");
    private static final Logger CLIENT_ERROR_LOG = LoggerFactory.getLogger("catcheck.telemetry.client-error");

    static final int MAX_CLIENT_ERROR_BYTES = 8 * 1024;

    static final int MAX_CSP_REPORT_BYTES = 16 * 1024;

    private final PublicIngressRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;

    public BrowserIngestController(PublicIngressRateLimiter rateLimiter, JsonMapper jsonMapper) {
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
    }


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


    private CspReportRequest parseCspReport(String rawBody) {
        try {
            return jsonMapper.readValue(rawBody, CspReportRequest.class);
        } catch (RuntimeException ex) {
            CSP_LOG.debug("Bo qua bao cao CSP khong doc duoc: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
