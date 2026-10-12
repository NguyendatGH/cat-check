package com.catcheck.credit.domain;

public enum PlanTier {

    MINI(1),

    DAILY(2),

    PLUS(3),

    MULTI(4),

    CARE_BOX(5);

    private final int rank;

    PlanTier(int rank) {
        this.rank = rank;
    }

    public String code() {
        return name();
    }

    public int rank() {
        return rank;
    }

    public static int rankOf(String packageCode) {
        for (PlanTier tier : values()) {
            if (tier.code().equals(packageCode)) {
                return tier.rank;
            }
        }
        return Integer.MAX_VALUE;
    }

    public static boolean isHigher(String candidate, String current) {
        if (current == null) {
            return true;
        }
        return rankOf(candidate) > rankOf(current);
    }
}
