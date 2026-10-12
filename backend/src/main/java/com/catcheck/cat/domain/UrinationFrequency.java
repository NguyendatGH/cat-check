package com.catcheck.cat.domain;

import java.util.Arrays;

public enum UrinationFrequency {

    LT_1_PER_DAY("LT_1_PER_DAY"),
    ONE_TO_TWO_PER_DAY("1_2_PER_DAY"),
    GT_2_PER_DAY("GT_2_PER_DAY"),
    UNKNOWN("UNKNOWN");

    private final String wireValue;

    UrinationFrequency(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static UrinationFrequency fromWire(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("urinationFrequency không hợp lệ: " + value));
    }
}
