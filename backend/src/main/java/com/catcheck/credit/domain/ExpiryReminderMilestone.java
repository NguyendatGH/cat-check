package com.catcheck.credit.domain;

import java.time.Duration;

public enum ExpiryReminderMilestone {

    T48H(Duration.ofHours(48), "CREDIT_EXPIRING_T48H"),

    T6H(Duration.ofHours(6), "CREDIT_EXPIRING_T6H");

    private final Duration lead;
    private final String templateCode;

    ExpiryReminderMilestone(Duration lead, String templateCode) {
        this.lead = lead;
        this.templateCode = templateCode;
    }

    public Duration lead() {
        return lead;
    }

    public String templateCode() {
        return templateCode;
    }
}
