package com.catcheck.identity.domain;

import java.security.SecureRandom;
import java.util.Locale;

public final class Base32 {

    private static final String RFC4648_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final String CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private Base32() {
    }

    public static String newTotpSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return encode(bytes, RFC4648_ALPHABET);
    }

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
