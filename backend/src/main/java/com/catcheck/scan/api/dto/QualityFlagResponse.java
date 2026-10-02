package com.catcheck.scan.api.dto;

/** Một cờ chất lượng (p6 §6.3.4, p4 D3) — {@code label}/{@code messageVi} tra ở client theo {@code messageKey}. */
public record QualityFlagResponse(String code, String severity, String messageKey) {
}
