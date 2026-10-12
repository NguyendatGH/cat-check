package com.catcheck.audit.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@org.springframework.modulith.NamedInterface("api")
public final class PiiRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            ".*("
                    + "password|passwd|passphrase"
                    + "|otp|otpcode|otp_code|code_hash|codehash"
                    + "|token|ticket|jwt|bearer|authorization|cookie|credential"
                    + "|secret|pepper|apikey|api_key|activationcode|activation_code|invitecode|invite_code"
                    + "|fid"
                    + "|phone|email|fullname|full_name|address|contact"
                    + ").*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_VALUE = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    private static final Pattern LONG_DIGITS = Pattern.compile("(?<!\\d)\\d{7,}(?!\\d)");

    private static final Pattern LONG_TOKEN_LIKE = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9+/=_\\-]{32,}(?![A-Za-z0-9])");

    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?<![A-Za-z0-9])[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?![A-Za-z0-9])");

    public Map<String, Object> redact(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return source == null ? null : Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> out.put(key, redactEntry(key, value)));
        return out;
    }

    private Object redactEntry(String key, Object value) {
        if (key != null && SENSITIVE_KEY.matcher(key).matches()) {
            return REDACTED;
        }
        return redactValue(value);
    }

    private Object redactValue(Object value) {
        if (value instanceof String text) {
            return scrubText(text);
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> converted = new LinkedHashMap<>(nested.size());
            nested.forEach((k, v) -> converted.put(String.valueOf(k), v));
            return redact(converted);
        }
        if (value instanceof Iterable<?> items) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (Object item : items) {
                out.add(redactValue(item));
            }
            return out;
        }
        return value;
    }

    private String scrubText(String text) {
        if (text.isEmpty()) {
            return text;
        }
        Matcher uuid = UUID_VALUE.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        while (uuid.find()) {
            out.append(scrubFragment(text.substring(last, uuid.start()))).append(uuid.group());
            last = uuid.end();
        }
        return out.append(scrubFragment(text.substring(last))).toString();
    }

    private static String scrubFragment(String text) {
        String out = EMAIL_VALUE.matcher(text).replaceAll(REDACTED + "_EMAIL");
        out = LONG_DIGITS.matcher(out).replaceAll(REDACTED + "_DIGITS");
        return LONG_TOKEN_LIKE.matcher(out).replaceAll(REDACTED + "_BLOB");
    }
}
