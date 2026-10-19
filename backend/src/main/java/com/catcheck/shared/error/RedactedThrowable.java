package com.catcheck.shared.error;

public final class RedactedThrowable extends Throwable {

    private static final int MAX_CAUSE_DEPTH = 16;

    private RedactedThrowable(String originalType, Throwable cause) {
        super(originalType, cause, false, true);
    }

    public static Throwable of(Throwable source) {
        return copy(source, 0);
    }

    private static RedactedThrowable copy(Throwable source, int depth) {
        Throwable cause = source.getCause();
        RedactedThrowable redactedCause = cause == null || cause == source || depth >= MAX_CAUSE_DEPTH
                ? null
                : copy(cause, depth + 1);
        RedactedThrowable copy = new RedactedThrowable(source.getClass().getName(), redactedCause);
        copy.setStackTrace(source.getStackTrace());
        return copy;
    }
}
