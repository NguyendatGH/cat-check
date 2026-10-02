package com.catcheck.identity.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Sinh ma OTP (p11 §11.2.1, S5: 6 chu so). Dung {@link SecureRandom}, KHONG dung
 * {@code Random}/{@code ThreadLocalRandom} — doan 6 chu so la nguon doan truc tiep
 * nen phai lay tu CSPRNG.
 *
 * <p>Khong phai mot Spring bean: doi tuong nay khong trang thai, tao moi cho moi ma
 * nen lop cha tu {@code new OtpCodeGenerator(...)} la du. Loi dung doan so ngau nhien
 * se bi chan boi ArchUnit {@code RandomnessRuleTests} (R12).</p>
 */
public final class OtpCodeGenerator {

    /** p11 §11.2.1: 6 chu so, va design {@code 01c-2} cung la 6 o. */
    public static final int CODE_LENGTH = 6;

    private final int length;
    private final int bound;
    private final SecureRandom random = new SecureRandom();

    public OtpCodeGenerator() {
        this(CODE_LENGTH);
    }

    /**
     * @param length so chu so, 6..8 (xem rang buoc {@code @Min(6) @Max(8)} cua
     *               {@code catcheck.auth.otp-code-length}). Cho phep truyen tham so
     *               de cau hinh chuot duoc noi, nhung mac dinh la 6 theo spec.
     */
    public OtpCodeGenerator(int length) {
        if (length < 6 || length > 8) {
            throw new IllegalArgumentException("otp-code-length phai trong [6, 8], nhung " + length);
        }
        this.length = length;
        this.bound = (int) Math.pow(10, length);
    }

    /**
     * Sinh ma, giu chu so 0 o dau vi xac thuc bang chuoi nen {@code 000123} va
     * {@code 123} phai la hai gia tri khac nhau.
     */
    public String generate() {
        // Locale.ROOT: %d o mot locale khong phai latin (vi du ar-EG) se ra chu so
        // khac '0'..'9' va ma se khong parse lai duoc o FE.
        return String.format(Locale.ROOT, "%0" + length + "d", random.nextInt(bound));
    }
}
