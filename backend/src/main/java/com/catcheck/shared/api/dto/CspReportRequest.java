package com.catcheck.shared.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CspReportRequest(@JsonProperty("csp-report") Report cspReport) {

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
