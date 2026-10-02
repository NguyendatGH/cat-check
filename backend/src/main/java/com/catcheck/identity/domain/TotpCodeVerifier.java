package com.catcheck.identity.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;

/**
 * TOTP RFC 6238 / HOTP RFC 4226 — tuyen JDK, KHONG dependency ngoai (p11 §11.12.2
 * chon phuong an A: TOTP la {@code HmacSHA1} cua {@code javax.crypto.Mac} cong vai
 * phep so hoc).
 *
 * <p>Cua so chay thu <b>+-1 buoc</b> (p11 §11.12.2) bu lech dong hong dien thoai;
 * +-2 tro len noi cua so tan cong gap doi ma khong giai quyet them ca thuc te nao.</p>
 *
 * <p>Khong mang annotation Spring (R8): day la thuat toan thuan, kiem duoc bang bo
 * test vector chuanh thuc cua RFC 6238.</p>
 */
public final class TotpCodeVerifier {

    /** p11 §11.12.2: HMAC-SHA1 la lua chon tuong thich, KHONG phai lua chon bao mat. */
    public static final String DEFAULT_ALGORITHM = "SHA1";

    private static final String[] ALGORITHMS = {"SHA1", "SHA256", "SHA512"};
    private static final int[] DIGITS_ALLOWED = {6, 8};
    private static final int[] PERIODS_ALLOWED = {30, 60};

    private TotpCodeVerifier() {
    }

    /**
     * Ma TOTP cua buoc {@code currentStep}.
     *
     * <p>Thuan tuy — khong tu doc dong ho he thong (R13). Ben goi (application layer, co
     * {@code Clock} inject) tinh {@code currentStep = clock.instant().getEpochSecond() / periodSeconds}
     * roi truyen vao.</p>
     *
     * @param secretBase32 secret da giai ma, Base32 khong padding (RFC 4648)
     */
    public static String now(String algorithm, int digits, String secretBase32, long currentStep) {
        return hotp(algorithm, digits, secretBase32, currentStep);
    }

    /**
     * Kiem tra ma nhap voi cua so +-1 buoc.
     *
     * @param currentStep  buoc thoi gian hien tai — do ben goi tinh tu {@code Clock} (R13)
     * @param lastUsedStep buoc thoi gian da tung dung — ma co step nay hoac nho hon
     *                     bi tu choi (chong replay, p11 §11.12.2)
     */
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

    /** Ma cua buoc thoi gian {@code step} — dung chung cho {@link #now} va {@link #verify}. */
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

    /** So sanh constant-time de khong lo thoi gian phan hoi tuy thuoc vi tri lech. */
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
