package com.catcheck.identity.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Base32 (RFC 4648, khong padding) cho secret TOTP, va Crockford Base32 cho ma
 * khoi phuc — hai bang chu cai khac nhau, tach class de khong nham.
 *
 * <p>Secret TOTP: 20 byte (160 bit) tu {@link SecureRandom}, hien thi Base32 khong
 * padding — dung dinh dang ma app authenticator doc (p11 §11.12.2).</p>
 *
 * <p>Ma khoi phuc: 10 ky tu <b>Crockford</b> Base32 (khong co {@code I}, {@code L},
 * {@code O}, {@code U} de khong doc nham khi chep tay), hien thi nhom
 * {@code XXXXX-XXXXX} (p11 §11.12.3).</p>
 */
public final class Base32 {

    /** RFC 4648 — bang chu cai cua secret TOTP. */
    private static final String RFC4648_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Crockford Base32 — loai I/L/O/U (nham voi 1/0), loai U vi hinh thuc
     * "ma co chua U" bi loc spam. p11 §11.12.3.
     */
    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Base32() {
    }

    /** Sinh secret TOTP 160 bit, tra ve Base32 khong padding. */
    public static String newTotpSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encode(bytes, RFC4648_ALPHABET);
    }

    /** Sinh ma khoi phuc 10 ky tu Crockford, tra ve dang {@code XXXXX-XXXXX}. */
    public static String newRecoveryCode() {
        byte[] bytes = new byte[7];
        RANDOM.nextBytes(bytes);
        String encoded = encode(bytes, CROCKFORD_ALPHABET).substring(0, 10);
        return encoded.substring(0, 5) + "-" + encoded.substring(5);
    }

    public static String encode(byte[] data, String alphabet) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(alphabet.charAt((buffer >>> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(alphabet.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    public static byte[] decode(String encoded) {
        String upper = encoded.trim().toUpperCase(Locale.ROOT).replace("-", "");
        int buffer = 0;
        int bitsLeft = 0;
        java.util.List<Byte> out = new java.util.ArrayList<>();
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            int value = RFC4648_ALPHABET.indexOf(ch);
            if (value < 0) {
                throw new IllegalArgumentException("Ky tu khong thuoc bang chu cai Base32: " + ch);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.add((byte) ((buffer >>> (bitsLeft - 8)) & 0xFF));
                bitsLeft -= 8;
            }
        }
        byte[] result = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            result[i] = out.get(i);
        }
        return result;
    }
}
