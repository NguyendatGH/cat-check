package com.catcheck.credit.domain;

public enum CreditLedgerType {

    GRANT,

    CONSUME,

    EXPIRE,

    REFUND,

    ADJUST;

    public boolean requiresNote() {
        return this == ADJUST;
    }
}
