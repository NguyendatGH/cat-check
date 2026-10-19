package com.catcheck.identity.application;

public record AuthRequestContext(String requestId, String ipAddress, String userAgent) {

    public static final AuthRequestContext UNKNOWN = new AuthRequestContext(null, null, null);

    public static AuthRequestContext of(String requestId, String ipAddress, String userAgent) {
        String requestIdValue = blankToNull(requestId);
        return new AuthRequestContext(
                requestIdValue,
                blankToNull(ipAddress),
                truncate(blankToNull(userAgent), 512));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
