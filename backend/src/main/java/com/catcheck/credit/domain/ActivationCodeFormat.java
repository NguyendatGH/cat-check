package com.catcheck.credit.domain;

import java.security.SecureRandom;

/**
 * Định dạng mã kích hoạt: {@code CC-<PKG>-<10 ký tự Crockford Base32>}, ví dụ
 * {@code CC-PLUS-7K3M9QX2RT} (p5 §5.9).
 *
 * <p><b>Cấu trúc 10 ký tự thân mã:</b> 9 ký tự ngẫu nhiên + 1 ký tự checksum ở CUỐI. Con số
 * này không phải suy diễn — p11 §11.7.4 viết: <i>"32¹⁰ ≈ 1,13 × 10¹⁵ tổ hợp, có checksum ở
 * ký tự cuối (thực tế còn ~3,5 × 10¹³ mã hợp lệ checksum)"</i> và 32⁹ = 3,517 × 10¹³ khớp
 * đúng con số đó. Nếu cả 10 ký tự đều ngẫu nhiên thì không gian hợp lệ vẫn là 1,13 × 10¹⁵
 * và tính "3,5 × 10¹³" ở p11 vô nghĩa. Vậy nên thân mã hữu hạn ở 9 ký tự ngẫu nhiên.</p>
 *
 * <p>Checksum ở ký tự cuối là để bắt lỗi gõ sai TRƯỚC khi gọi API (p5 §5.9, p8
 * {@code ACTIVATION_CODE_MALFORMED}) — không phải để chống dò, việc đó là của HMAC + pepper
 * (p11 §11.7.4) và rate limit (p11 §11.7.2).</p>
 *
 * <p>Crockford Base32 bỏ các ký tự dễ nhầm {@code I, L, O, U} — ký tự gõ tay trên bao bì và
 * bấm vào điện thoại.</p>
 */
public final class ActivationCodeFormat {

    /** Bảng chữ cái Crockford Base32, 32 ký tự, loại bỏ {@code I}, {@code L}, {@code O}, {@code U}. */
    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Số ký tự thân mã (9 ngẫu nhiên + 1 checksum). */
    public static final int BODY_LENGTH = 10;

    /** Số ký tự ngẫu nhiên; ký tự cuối là checksum. */
    public static final int RANDOM_LENGTH = BODY_LENGTH - 1;

    /** Hằt tiền tố của mọi mã. */
    public static final String PREFIX = "CC-";

    /** Độ dài tối đa của {@code activation_code.code_prefix} (p5 §5.5). */
    public static final int MAX_PREFIX_LENGTH = 8;

    private static final int ALPHABET_SIZE = ALPHABET.length();

    private ActivationCodeFormat() {
    }

    /**
     * Dạng mã để NHẮC người dùng khi họ gõ sai — tham số cho thông điệp lỗi
     * {@code ACTIVATION_CODE_MALFORMED}. Để ở đây để thông điệp lỗi, DTO và tầng nghiệp vụ
     * không tự viết lại chuỗi này rồi lệch nhau.
     */
    public static String expectedFormat() {
        return PREFIX + "<GÓI>-" + BODY_LENGTH + " KÝ TỰ";
    }

    /**
     * Sinh một mã mới. Nguồn ngẫu nhiên do bên gọi đưa vào (p5 §5.9: "nguồn ngẫu nhiên an toàn")
     * — domain không tự khởi tạo {@link SecureRandom} để việc kiểm thử không phụ thuộc điều kiện
     * nhân sự.
     *
     * @param packageCode mã gói, ví dụ {@code "PLUS"}
     * @param random      nguồn ngẫu nhiên mật mã
     * @return mã dạng {@code CC-<PKG>-<10 ký tự>}
     */
    public static String generate(String packageCode, SecureRandom random) {
        requirePackageCode(packageCode);
        StringBuilder body = new StringBuilder(BODY_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            body.append(ALPHABET.charAt(random.nextInt(ALPHABET_SIZE)));
        }
        body.append(checksumChar(body));
        return PREFIX + packageCode + "-" + body;
    }

    /**
     * Chuẩn hoá mã người dùng nhập: bỏ khoảng trắng thừa, đổi chữ thường thành hoa, và ánh xạ
     * ba chữ dễ nhầm theo đúng quy tắc Crockford ({@code I, L} → {@code 1}, {@code O} → {@code 0}).
     *
     * <p>Ánh xạ này phải áp dụng TRƯỚC khi băm, không chỉ trước khi so checksum: nếu băm mã
     * thô thì một mã bị gõ nhầm {@code O} thành {@code 0} sẽ ra hash khác và không bao giờ
     * khớp dù người dùng gõ đúng ý.</p>
     *
     * <p><b>Ánh xạ CHỈ áp cho thân mã</b> (phần sau dấu gạch ngang cuối), không phải tiền tố gói:
     * các gói {@code MINI}/{@code DAILY} chứa {@code I} và {@code PLUS} chứa {@code L}, nếu ánh xả
     * cả tiền tố thì mã đúng {@code CC-PLUS-…} sẽ bị thành {@code CC-P1US-…} và không bao giờ khớp.
     * Tiền tố gói giữ nguyên ký tự — sai tiền tố là mã không tồn tại, trả
     * {@code ACTIVATION_CODE_INVALID} (p8 §8.2.4(f)).</p>
     */
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

    /**
     * Kiểm tra định dạng + checksum. Trả về mã đã chuẩn hoá nếu hợp lệ, {@code null} nếu không.
     *
     * <p>Chạy TRƯỚC khi tra DB (p8 {@code ACTIVATION_CODE_MALFORMED} — "bắt được trước khi tra
     * DB"): một mã sai định dạng không bao giờ là mã hợp lệ nên không tốn một lần tra hash.</p>
     *
     * @param rawCode mã người dùng nhập hoặc quét từ QR
     */
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

    /**
     * Tiền tố lưu vào {@code activation_code.code_prefix} để hỗ trợ tra cứu: mã gói kèm dấu
     * gạch ngang nếu còn đủ chỗ trong 8 ký tự.
     *
     * <p>{@code CARE_BOX-} dài 9 ký tự nên bị cắt còn {@code CARE_BOX}. Cột này CHỈ là gợi ý
     * cho tổng đài tra cứu (index {@code (code_prefix, status)}) — khoá tra cứu thật là
     * {@code code_hash}, nên việc cắt này không ảnh hưởng tính đúng đắn.</p>
     */
    public static String codePrefixOf(String packageCode) {
        requirePackageCode(packageCode);
        String prefix = packageCode + "-";
        return prefix.length() <= MAX_PREFIX_LENGTH ? prefix : packageCode.substring(0, MAX_PREFIX_LENGTH);
    }

    /**
     * Ký tự checksum theo đặc tả Crockford: tổng có trọng số với đáy 31, lấy modulo 32 rồi bù để
     * tổng cộng với checksum ra 0 modulo 32.
     */
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
