package com.catcheck.identity.domain;

import java.security.SecureRandom;
import java.util.Locale;

public final class OtpCodeGenerator {

    public static final int CODE_LENGTH = 6;

    private final int length;
    private final int bound;
    private final SecureRandom random = new SecureRandom();

    public OtpCodeGenerator() {
        this(CODE_LENGTH);
    }

    public OtpCodeGenerator(int length) {
        if (length < 6 || length > 8) {
            throw new IllegalArgumentException("otp-code-length phai trong [6, 8], nhung " + length);
        }
        this.length = length;
        this.bound = (int) Math.pow(10, length);
    }

    public String generate() {
        return String.format(Locale.ROOT, "%0" + length + "d", random.nextInt(bound));
    }
}
