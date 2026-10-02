package com.catcheck.insight.domain;

/** Mức độ nghiêm trọng của một dấu hiệu — cột {@code health_flag.severity}/{@code monitoring_rule.severity} (p4 D11/D12). */
public enum HealthFlagSeverity {
    INFO,
    ATTENTION,
    URGENT
}
