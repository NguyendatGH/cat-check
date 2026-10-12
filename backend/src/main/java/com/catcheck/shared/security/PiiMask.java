package com.catcheck.shared.security;

public final class PiiMask {

    private static final int EMAIL_LOCAL_VISIBLE = 3;

    private static final int PHONE_EDGE_VISIBLE = 3;

    private static final String FULLY_MASKED = "***";

    private PiiMask() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return FULLY_MASKED;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String visible = local.length() <= EMAIL_LOCAL_VISIBLE
                ? "" : local.substring(0, EMAIL_LOCAL_VISIBLE);
        return visible + FULLY_MASKED + "@" + domain;
    }

    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.strip();
        if (digits.length() <= PHONE_EDGE_VISIBLE * 2) {
            return FULLY_MASKED;
        }
        int hidden = digits.length() - PHONE_EDGE_VISIBLE * 2;
        return digits.substring(0, PHONE_EDGE_VISIBLE)
                + "*".repeat(hidden)
                + digits.substring(digits.length() - PHONE_EDGE_VISIBLE);
    }

    public static String ipAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        if (ip.indexOf(':') >= 0) {
            String[] groups = ip.split(":");
            if (groups.length <= 3) {
                return FULLY_MASKED;
            }
            return String.join(":", groups[0], groups[1], groups[2]) + ":*";
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) + ".*" : FULLY_MASKED;
    }
}
