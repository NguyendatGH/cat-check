package com.catcheck.shared.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Than cua K1 {@code POST /api/v1/csp-report} — dung dinh dang
 * {@code application/csp-report} ma trinh duyet gui: mot object boc ngoai ten
 * {@code "csp-report"}.
 *
 * <p><b>{@code ignoreUnknown = true} la mot quyet dinh BAO MAT, khong phai de de tinh.</b>
 * Trinh duyet khac nhau gui bo truong khac nhau, va phien ban Reporting API moi con gui ca
 * {@code sample} — doan ma nguon quanh cho vi pham, tuc la <b>noi dung trang</b>. Bo moi truong
 * khong khai bao ngay o tang parse nghia la nhung truong do khong ton tai trong tien trinh nay:
 * khong the log, khong the luu, khong the ro. Doi lai bang {@code 400} se khien bao cao CSP cua
 * mot phien ban trinh duyet moi bien mat, va p11 §11.8.3 ghi ro hai tuan doc bao cao o
 * staging/prod la buoc bat buoc truoc khi chuyen CSP sang che do chan.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CspReportRequest(@JsonProperty("csp-report") Report cspReport) {

    /**
     * Cac truong cua bao cao. Tat ca deu {@code String}/{@code Integer} (kieu boc, H15.99) va
     * <b>khong co</b> {@code @NotNull}: p8 K1 chot "luon tra 204" nen mot bao cao thieu truong
     * phai duoc bo qua trong im lang, khong duoc thanh mot loi validate.
     *
     * @param sourceFile khong phai PII nhung co the chua duong dan day du — controller loc
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            @JsonProperty("document-uri") String documentUri,
            @JsonProperty("referrer") String referrer,
            @JsonProperty("violated-directive") String violatedDirective,
            @JsonProperty("effective-directive") String effectiveDirective,
            @JsonProperty("original-policy") String originalPolicy,
            @JsonProperty("blocked-uri") String blockedUri,
            @JsonProperty("disposition") String disposition,
            @JsonProperty("status-code") Integer statusCode,
            @JsonProperty("source-file") String sourceFile,
            @JsonProperty("line-number") Integer lineNumber,
            @JsonProperty("column-number") Integer columnNumber) {
    }
}
