package com.catcheck.credit.domain;

public enum HistoryLevel {

    NONE,

    BASIC,

    ADVANCED;

    public boolean grantsHistory() {
        return this != NONE;
    }
}
