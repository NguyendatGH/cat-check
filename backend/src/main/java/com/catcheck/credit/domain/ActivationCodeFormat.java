package com.catcheck.credit.domain;

import java.security.SecureRandom;

public final class ActivationCodeFormat {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static final int BODY_LENGTH = 10;

    public static final int RANDOM_LENGTH = BODY_LENGTH - 1;

    public static final String PREFIX = "CC-";

    public static final int MAX_PREFIX_LENGTH = 8;

    private static final int ALPHABET_SIZE = ALPHABET.length();

    private ActivationCodeFormat() {
    }

    public static String expectedFormat() {
        return PREFIX + "<GÓI>-" + BODY_LENGTH + " KÝ TỰ";
    }

    public static String generate(String packageCode, SecureRandom random) {
        requirePackageCode(packageCode);
        StringBuilder body = new StringBuilder(BODY_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            body.append(ALPHABET.charAt(random.nextInt(ALPHABET_SIZE)));
        }
        body.append(checksumChar(body));
        return PREFIX + packageCode + "-" + body;
    }

    public static String normalize(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        String trimmed = rawCode.strip().toUpperCase(java.util.Locale.ROOT);
        int lastDash = trimmed.lastIndexOf('-');
        if (lastDash < 0) {
            return trimmed;
        }
        String packagePart = trimmed.substring(0, lastDash + 1);
        String body = trimmed.substring(lastDash + 1);
        StringBuilder out = new StringBuilder(trimmed.length());
        out.append(packagePart);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            out.append(switch (c) {
                case 'I', 'L' -> '1';
                case 'O' -> '0';
                default -> c;
            });
        }
        return out.toString();
    }

    public static String validateOrNull(String rawCode) {
        String normalized = normalize(rawCode);
        int lastDash = normalized.lastIndexOf('-');
        if (lastDash < 0) {
            return null;
        }
        String body = normalized.substring(lastDash + 1);
        if (body.length() != BODY_LENGTH) {
            return null;
        }
        StringBuilder randomPart = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < BODY_LENGTH; i++) {
            int value = valueOf(body.charAt(i));
            if (value < 0) {
                return null;
            }
            if (i < RANDOM_LENGTH) {
                randomPart.append(body.charAt(i));
            }
        }
        return checksumChar(randomPart) == body.charAt(BODY_LENGTH - 1) ? normalized : null;
    }

    public static String codePrefixOf(String packageCode) {
        requirePackageCode(packageCode);
        String prefix = packageCode + "-";
        return prefix.length() <= MAX_PREFIX_LENGTH ? prefix : packageCode.substring(0, MAX_PREFIX_LENGTH);
    }

    private static char checksumChar(CharSequence randomPart) {
        int sum = 0;
        for (int i = 0; i < randomPart.length(); i++) {
            sum += valueOf(randomPart.charAt(i)) * intPow(31, randomPart.length() - 1 - i);
        }
        return ALPHABET.charAt((ALPHABET_SIZE - (sum % ALPHABET_SIZE)) % ALPHABET_SIZE);
    }

    private static int valueOf(char c) {
        return ALPHABET.indexOf(c);
    }

    private static int intPow(int base, int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result = (result * base) % ALPHABET_SIZE;
        }
        return result;
    }

    private static void requirePackageCode(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            throw new IllegalArgumentException("packageCode phải có giá trị");
        }
    }
}
