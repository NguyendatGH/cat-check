package com.catcheck.insight.domain;

/** Mã rule — {@code monitoring_rule.code} / {@code health_flag.rule_code} (p6 §6.9). */
public enum MonitoringRuleCode {
    REPEATED_OUT_OF_RANGE,
    BASELINE_DEVIATION,
    MONOTONIC_TREND,
    LOW_QUALITY_STREAK,
    URGENT_CLINICAL_SIGN
}
