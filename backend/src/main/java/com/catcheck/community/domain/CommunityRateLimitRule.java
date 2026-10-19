package com.catcheck.community.domain;

public enum CommunityRateLimitRule {

    POST_CREATE_BY_USER("5/10m"),

    COMMENT_CREATE_BY_USER("20/10m"),

    REPORT_CREATE_BY_USER("10/10m");

    private final String documentedRate;

    CommunityRateLimitRule(String documentedRate) {
        this.documentedRate = documentedRate;
    }

    public String documentedRate() {
        return documentedRate;
    }

    public String bucketNamespace() {
        return name();
    }
}
