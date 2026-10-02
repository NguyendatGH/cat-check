package com.catcheck.identity.domain;

/**
 * Ngon ngu ui. Khop {@code app_user.locale} (p4 §4.4.2).
 *
 * <p>Khong dung truc tiep {@link java.util.Locale} trong code vi se do lai khoong
 * chua p11 §11.2.7 yeu cau moi noi dung nho phai lay theo key da gan nghiep vu.</p>
 */
public enum AppLocale {

    VI("vi"),
    EN("en");

    private final String code;

    AppLocale(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AppLocale fromCode(String value) {
        for (AppLocale candidate : values()) {
            if (candidate.code.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Locale khong ho tro: " + value);
    }
}
