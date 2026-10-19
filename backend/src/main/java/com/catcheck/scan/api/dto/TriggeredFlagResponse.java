package com.catcheck.scan.api.dto;

public record TriggeredFlagResponse(String flagId, String ruleCode, String severity, String messageKey) {
}
