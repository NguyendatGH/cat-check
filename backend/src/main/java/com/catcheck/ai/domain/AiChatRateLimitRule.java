package com.catcheck.ai.domain;

public enum AiChatRateLimitRule {

    AI_CHAT_BY_USER("20/1h");

    private final String documentedRate;

    AiChatRateLimitRule(String documentedRate) {
        this.documentedRate = documentedRate;
    }

    public String documentedRate() {
        return documentedRate;
    }

    public String bucketNamespace() {
        return name();
    }
}
