package com.catcheck.credit.domain;

public enum PlanFeature {

    HISTORY("history"),

    TREND("trend"),

    REMINDER("reminder"),

    EXPORT("export"),

    STORE_IMAGE("storeImage");

    private final String jsonKey;

    PlanFeature(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    public String jsonKey() {
        return jsonKey;
    }
}
