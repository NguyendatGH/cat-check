package com.catcheck.audit.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Che PII truoc khi ghi {@code audit_log.metadata/before/after}.
 *
 * <p>p4 §4.6.4: cac cot nay "KHONG bao gio chua PII tho — mat khau, OTP, token, secret,
 * khoa, cau hinh"; chi ghi id, loai, hash. p11 §11.10.3 nhom lai: "chi ghi id, loai,
 * hash — khong ghi ten, email, so dien thoai". Che o TANG UNG DUNG (R2: khong
 * phai trong adapter JDBC) de moi duong ghi audit deu di qua mot noi.</p>
 *
 * <p>Phong bi (defence in depth), KHONG phay thay the quy tac cua nguoi goi: tai
 * lop trung gian co the truyen nham mot gia tri doc lap vao chuoi (vi du ghi chuoi
 * ban ghi vao {@code note}) va regex o day se khong bat duoc.</p>
 *
 * <p>{@code @NamedInterface("api")}: type nay vat ly nam o {@code audit.application} nhung module
 * {@code identity} dung truc tiep de tu che PII truoc khi goi audit (xem
 * {@code IdentityConfiguration.piiRedactor()}). Gan thang annotation len type (khong doi package)
 * de gop vao named interface "api" da co cua audit — cung co che voi
 * {@code credit.domain.CreditLedgerRefType}, xem javadoc o do.</p>
 */
@org.springframework.modulith.NamedInterface("api")
public final class PiiRedactor {

    public static final String REDACTED = "[REDACTED]";

    /**
     * Khop khoa nhay cam. Phai bao phu MOT chuoi con dau tien CI la chuoi trong
     * quy tac CI chan PII (p17 §17.10b) — {@code otp}, {@code token}, {@code secret},
     * {@code password}, {@code activationCode}, {@code fid}, {@code apiKey}.
     * Dinh nghia bang regex chu khong phai {@code contains} de tranh bo sot bien theo
     * kieu viet camelCase/snake_case.
     */
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

    /** Gia tri chuoi co hinh dang email — p11 §11.10.3 cam ghi email vao audit. */
    private static final Pattern EMAIL_VALUE = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    /**
     * Day so dai >= 7 — so dien thoai, so CCCD, ma giao dich. Ngan phai khop nua chuoi
     * {@code 2026-01-15} hay {@code 42} trong metadata.
     */
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<!\\d)\\d{7,}(?!\\d)");

    /** Gia tri chuoi dai >= 32, nghi la JWT/hex blob. */
    private static final Pattern LONG_TOKEN_LIKE = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9+/=_\\-]{32,}(?![A-Za-z0-9])");

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
        // So, boolean, UUID, enum, Instant: khong can che.
        return value;
    }

    private String scrubText(String text) {
        if (text.isEmpty()) {
            return text;
        }
        String out = EMAIL_VALUE.matcher(text).replaceAll(REDACTED + "_EMAIL");
        out = LONG_DIGITS.matcher(out).replaceAll(REDACTED + "_DIGITS");
        return LONG_TOKEN_LIKE.matcher(out).replaceAll(REDACTED + "_BLOB");
    }
}
