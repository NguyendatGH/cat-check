package com.catcheck.identity.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;

public final class TotpCodeVerifier {

    public static final String DEFAULT_ALGORITHM = "SHA1";

    private static final String[] ALGORITHMS = {"SHA1", "SHA256", "SHA512"};
    private static final int[] DIGITS_ALLOWED = {6, 8};
    private static final int[] PERIODS_ALLOWED = {30, 60};

    private TotpCodeVerifier() {
    }

    public static String now(String algorithm, int digits, String secretBase32, long currentStep) {
        return hotp(algorithm, digits, secretBase32, currentStep);
    }

    public static boolean verify(String algorithm, int digits, String secretBase32,
                                 String submittedCode, long lastUsedStep, long currentStep) {
        if (submittedCode == null || submittedCode.length() != digits) {
            return false;
        }
        for (int i = 0; i < digits; i++) {
            if (!Character.isDigit(submittedCode.charAt(i))) {
                return false;
            }
        }
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (step <= lastUsedStep) {
                continue;
            }
            String candidate = hotp(algorithm, digits, secretBase32, step);
            if (constantTimeEquals(candidate, submittedCode)) {
                return true;
            }
        }
        return false;
    }

    public static String hotp(String algorithm, int digits, String base32Secret, long step) {
        byte[] key = Base32.decode(base32Secret);
        byte[] counter = ByteBuffer.allocate(8).putLong(step).array();
        String hmacAlgorithm = "Hmac" + algorithm.toUpperCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(hmacAlgorithm);
            mac.init(new SecretKeySpec(key, hmacAlgorithm));
            byte[] hash = mac.doFinal(counter);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int modulus = (int) Math.pow(10, digits);
            int otp = binary % modulus;
            return String.format(Locale.ROOT, "%0" + digits + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("JCA khong ho tro " + hmacAlgorithm, ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    public static boolean isAlgorithmSupported(String algorithm) {
        for (String candidate : ALGORITHMS) {
            if (candidate.equalsIgnoreCase(algorithm)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDigitsSupported(int digits) {
        for (int candidate : DIGITS_ALLOWED) {
            if (candidate == digits) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPeriodSupported(int periodSeconds) {
        for (int candidate : PERIODS_ALLOWED) {
            if (candidate == periodSeconds) {
                return true;
            }
        }
        return false;
    }
}
