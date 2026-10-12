package com.catcheck.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public record OtpTicket(String rawValue, Instant expiresAt) {

    public static final int BYTE_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public OtpTicket {
        Objects.requireNonNull(rawValue, "ticket khong duoc null");
        Objects.requireNonNull(expiresAt, "han dung khong duoc null");
    }

    public static String newRawValue() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static OtpTicket create(Instant expiresAt) {
        return new OtpTicket(newRawValue(), expiresAt);
    }

    public static String hashOf(String rawTicket) {
        Objects.requireNonNull(rawTicket, "ticket khong duoc null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawTicket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JCA khong co SHA-256", ex);
        }
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
