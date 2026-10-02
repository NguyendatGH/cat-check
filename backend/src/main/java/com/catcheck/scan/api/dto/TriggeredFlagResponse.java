package com.catcheck.scan.api.dto;

/** Một health_flag vừa bắn trong lần quét này (p8 §8.5.4 {@code triggeredFlags[]}). */
public record TriggeredFlagResponse(String flagId, String ruleCode, String severity, String messageKey) {
}
