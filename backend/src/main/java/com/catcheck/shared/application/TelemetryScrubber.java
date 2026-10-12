package com.catcheck.shared.application;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TelemetryScrubber {

    public static final String REDACTED = "[redacted]";

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    private static final Pattern PHONE_VN = Pattern.compile(
            "(?<![\\dA-Za-z])(?:\\+?84|0)[\\s.\\-]?\\d{2}[\\s.\\-]?\\d{3,4}[\\s.\\-]?\\d{3,4}(?![\\dA-Za-z])");

    private static final Pattern LONG_OPAQUE_BLOB = Pattern.compile("[A-Za-z0-9+/_=\\-]{40,}");

    private static final Pattern JWT_LIKE = Pattern.compile(
            "eyJ[A-Za-z0-9+/_=\\-]{8,}\\.[A-Za-z0-9+/_=\\-]{8,}(?:\\.[A-Za-z0-9+/_=\\-]+)?");

    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)\\b(bearer|basic|token)\\s+[A-Za-z0-9+/_=.\\-]{8,}");

    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|otp|code|credential|authorization|auth|jwt|bearer"
                    + "|session|sessionid|csrf|xsrf|activation|activationcode|apikey|api_key"
                    + "|accesstoken|access_token|refreshtoken|refresh_token|secret|token|fid"
                    + "|email|phone|fullname|full_name)\\b\\s*[=:]\\s*[^\\s&;,)\\]}\"']+");

    private static final Pattern INLINE_DATA_URI = Pattern.compile("(?i)\\b(data|blob):[^\\s\"')]+");

    private static final int MAX_URI_LENGTH = 512;

    private TelemetryScrubber() {
    }

    public static String scrubText(String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip();
        out = SENSITIVE_PAIR.matcher(out).replaceAll(match -> match.group(1) + "=" + REDACTED);
        out = BEARER_VALUE.matcher(out).replaceAll(match -> match.group(1) + " " + REDACTED);
        out = JWT_LIKE.matcher(out).replaceAll(REDACTED);
        out = INLINE_DATA_URI.matcher(out).replaceAll(match -> match.group(1) + ":" + REDACTED);
        out = EMAIL.matcher(out).replaceAll(REDACTED);
        out = PHONE_VN.matcher(out).replaceAll(REDACTED);
        out = LONG_OPAQUE_BLOB.matcher(out).replaceAll(REDACTED);
        out = SIX_DIGIT_CODE.matcher(out).replaceAll(REDACTED);
        out = out.replaceAll("[\\p{Cntrl}]+", " ").replaceAll(" {2,}", " ").strip();
        if (maxChars > 0 && out.length() > maxChars) {
            out = out.substring(0, maxChars) + "…";
        }
        return out.isBlank() ? null : out;
    }

    public static String scrubUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip();
        int cut = indexOfFirst(out, '?', '#', ';');
        if (cut >= 0) {
            out = out.substring(0, cut);
        }
        return scrubText(out, MAX_URI_LENGTH);
    }

    public static String scrubToken(String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip().replaceAll("[^A-Za-z0-9._\\-+:/ ]", "");
        if (maxChars > 0 && out.length() > maxChars) {
            out = out.substring(0, maxChars);
        }
        return out.isBlank() ? null : out;
    }

    public static String scrubOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip();
        if (!out.contains("//")) {
            return scrubToken(out, 64);
        }
        int schemeEnd = out.indexOf("//") + 2;
        int pathStart = indexOfFrom(out, schemeEnd, '/', '?', '#');
        String origin = pathStart < 0 ? out : out.substring(0, pathStart);
        return scrubToken(origin.toLowerCase(Locale.ROOT), 160);
    }

    private static int indexOfFirst(String value, char... chars) {
        return indexOfFrom(value, 0, chars);
    }

    private static int indexOfFrom(String value, int from, char... chars) {
        int best = -1;
        for (char candidate : chars) {
            int at = value.indexOf(candidate, from);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }
}
