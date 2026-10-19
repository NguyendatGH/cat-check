package com.catcheck.identity.domain;

import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    private static final int MIN_REPEATED_RUN = 4;
    private static final int MIN_SEQUENTIAL_RUN = 4;

    private final Set<String> blockedPasswords;

    public PasswordPolicy(Set<String> blockedPasswords) {
        this.blockedPasswords = Set.copyOf(blockedPasswords);
    }

    public enum Violation {
        TOO_SHORT,
        TOO_LONG,
        REPEATED_CHARS,
        SEQUENTIAL_CHARS,
        BLOCKED
    }

    public Violation check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Violation.TOO_SHORT;
        }
        if (password.length() > MAX_LENGTH) {
            return Violation.TOO_LONG;
        }
        if (hasLongRepeatRun(password)) {
            return Violation.REPEATED_CHARS;
        }
        if (hasLongSequentialRun(password)) {
            return Violation.SEQUENTIAL_CHARS;
        }
        if (blockedPasswords.contains(normalizeForComparison(password))) {
            return Violation.BLOCKED;
        }
        return null;
    }

    public boolean isValid(String password) {
        return check(password) == null;
    }

    private static boolean hasLongRepeatRun(String password) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                run++;
                if (run >= MIN_REPEATED_RUN) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    private static boolean hasLongSequentialRun(String password) {
        return hasRunOfStep(password, 1) || hasRunOfStep(password, -1);
    }

    private static boolean hasRunOfStep(String password, int step) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) - password.charAt(i - 1) == step) {
                run++;
                if (run >= MIN_SEQUENTIAL_RUN) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    static String normalizeForComparison(String password) {
        StringBuilder out = new StringBuilder(password.length());
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (!Character.isWhitespace(ch)) {
                out.append(Character.toLowerCase(ch));
            }
        }
        return out.toString();
    }
}
