package com.catcheck.identity.domain;

import java.util.Set;

/**
 * Chinh sach mat khau (p11 S4, §11.3.1 va §11.3.5, khop p8 §8.4 error {@code PASSWORD_BREACHED}).
 *
 * <p>Quyet dinh NIST SP 800-63B ma p11 trich dan: bo yeu cau ky tu dac biet, thay bang
 * <b>do dai toi thieu</b> + <b>chan mat khau da bi lo</b>. Yeu cau ky tu dac biet khong
 * tang entropy thuc te ma chi tao hanh vi du doan duoc ({@code Password1!}).</p>
 *
 * <p>Ba lop kiem tra, theo thu tu:</p>
 * <ol>
 *   <li>Do dai trong [8, 72]. 72 la gioi han byte cua BCrypt — dung byte, khong dung
 *       ky tu, va KHONG cat am tham (p11 §11.3.1: &gt;72 -&gt; 400 chu khong phai cat).</li>
 *   <li>Chuoi lap lien tiep (>= 4 ky tu giong nhau): {@code 1111}, {@code aaaaaaaa}.</li>
 *   <li>Chuoi tu/tuan tự don gian (>= 4 ky tu): {@code 1234}, {@code abcdefgh}, {@code 4321}.</li>
 *   <li>Danh sach mat khau pho bien da chuan hoa (lowercase + bo khoang trang).</li>
 * </ol>
 *
 * <p>Lop 2 va 3 la quy tac <b>duong danh sach</b>: p11 §11.3.5 ghi {@code 111111},
 * {@code abcdefgh}, {@code 12345678} "da nam trong top 10k" — tuc la chung duoc chan
 * bang danh sach. NIST 800-63B-4 cung yeu cau chan ro rang chuoi lap va chuoi tu, va
 * quy tac nay chan duoc ca nhung mau <b>khong</b> nam trong top 10k, khong can tai
 * duoc danh sach 10.000 muc.</p>
 */
public final class PasswordPolicy {

    /** p11 S4: min 8 / max 72. */
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    /**
     * Do dai chuoi lap/toi thieu bi chan. 4 la nguong ma NIST 800-63B-4 dung vi du
     * ({@code 1111}, {@code aaaaaaa}); p11 dua vi du {@code 111111} va {@code 12345678}
     * cung deu dai hon.
     */
    private static final int MIN_REPEATED_RUN = 4;
    private static final int MIN_SEQUENTIAL_RUN = 4;

    private final Set<String> blockedPasswords;

    public PasswordPolicy(Set<String> blockedPasswords) {
        this.blockedPasswords = Set.copyOf(blockedPasswords);
    }

    public enum Violation {
        TOO_SHORT,
        TOO_LONG,
        REPEATED_CHARS,
        SEQUENTIAL_CHARS,
        BLOCKED
    }

    /**
     * @return {@code null} neu mat khau dat; nguoc lai tra ve {@link Violation} dau tien
     *         theo thu tu kiem tra. Moi {@code Violation} deu tra cung mot error code
     *         {@code PASSWORD_BREACHED}/{@code PASSWORD_TOO_SHORT}/... o tang api —
     *         nguoi dung khong can biet mat khau vi duoc quy nao.
     */
    public Violation check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Violation.TOO_SHORT;
        }
        if (password.length() > MAX_LENGTH) {
            return Violation.TOO_LONG;
        }
        if (hasLongRepeatRun(password)) {
            return Violation.REPEATED_CHARS;
        }
        if (hasLongSequentialRun(password)) {
            return Violation.SEQUENTIAL_CHARS;
        }
        if (blockedPasswords.contains(normalizeForComparison(password))) {
            return Violation.BLOCKED;
        }
        return null;
    }

    public boolean isValid(String password) {
        return check(password) == null;
    }

    /** Co chay >= {@value #MIN_REPEATED_RUN} ky tu giong het nhau lien tiep khong. */
    private static boolean hasLongRepeatRun(String password) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                run++;
                if (run >= MIN_REPEATED_RUN) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    /**
     * Co chay >= {@value #MIN_SEQUENTIAL_RUN} ky tu lien tiep theo he so 1
     * ({@code 1234}, {@code abcdefgh}) hoac theo he so -1 ({@code 4321}).
     *
     * <p>Chi xet ASCII de truong hop tep {@code 'a'+1 == 'b'}. Kiem tra kieu nay
     * khong chan dung nhung chuoi co chu hoa/thuong xen nhau ({@code AbCdEf}) — dung
     * nhu vay vi nguoi dung hay tu viet lai mat khau theo kieu chay chu.</p>
     */
    private static boolean hasLongSequentialRun(String password) {
        return hasRunOfStep(password, 1) || hasRunOfStep(password, -1);
    }

    private static boolean hasRunOfStep(String password, int step) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) - password.charAt(i - 1) == step) {
                run++;
                if (run >= MIN_SEQUENTIAL_RUN) {
                    return true;
                }
            } else {
                run = 1;
            }
        }
        return false;
    }

    /**
     * Chuan hoa de so khop: lowercase + bo khoang trang (p11 §11.3.5).
     * KHONG bo ky tu dac biet hay ky tu lap lai — danh sach chua san da lo.
     */
    static String normalizeForComparison(String password) {
        StringBuilder out = new StringBuilder(password.length());
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (!Character.isWhitespace(ch)) {
                out.append(Character.toLowerCase(ch));
            }
        }
        return out.toString();
    }
}
