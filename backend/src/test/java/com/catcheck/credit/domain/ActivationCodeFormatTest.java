package com.catcheck.credit.domain;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Định dạng mã kích hoạt {@code CC-<PKG>-<10 ký tự Crockford Base32>} (p5 §5.9).
 *
 * <p>Kiểm chứng ba thứ: (1) đúng bảng chữ cái Crockford — không I/L/O/U; (2) checksum ở ký tự
 * cuối bắt được một ký tự bị sai; (3) chuẩn hoá ánh xạ I/L→1, O→0 TRƯỚC khi băm.</p>
 */
class ActivationCodeFormatTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void generatedCodeMatchesFormat() {
        String code = ActivationCodeFormat.generate("PLUS", random);
        assertTrue(code.startsWith("CC-PLUS-"), code);
        String body = code.substring("CC-PLUS-".length());
        assertEquals(10, body.length(), code);
        for (int i = 0; i < body.length(); i++) {
            assertTrue(ActivationCodeFormat.ALPHABET.indexOf(body.charAt(i)) >= 0,
                    "Ký tự ngoài bảng Crockford: " + body.charAt(i));
        }
    }

    @Test
    void alphabetExcludesConfusableCharacters() {
        assertFalse(ActivationCodeFormat.ALPHABET.contains("I"));
        assertFalse(ActivationCodeFormat.ALPHABET.contains("L"));
        assertFalse(ActivationCodeFormat.ALPHABET.contains("O"));
        assertFalse(ActivationCodeFormat.ALPHABET.contains("U"));
        assertEquals(32, ActivationCodeFormat.ALPHABET.length());
    }

    @Test
    void validCodePassesValidation() {
        String code = ActivationCodeFormat.generate("PLUS", random);
        assertEquals(code, ActivationCodeFormat.validateOrNull(code));
    }

    @Test
    void validationCatchesSingleCharacterTypo() {
        String code = ActivationCodeFormat.generate("DAILY", random);
        String body = code.substring(code.lastIndexOf('-') + 1);
        char wrong = body.charAt(0) == '0' ? '1' : '0';
        String tampered = code.substring(0, code.lastIndexOf('-') + 1) + wrong + body.substring(1);
        assertNotEquals(code, tampered);
        assertNull(ActivationCodeFormat.validateOrNull(tampered));
    }

    @Test
    void validationRejectsWrongLength() {
        assertNull(ActivationCodeFormat.validateOrNull("CC-PLUS-ABC"));
        assertNull(ActivationCodeFormat.validateOrNull("CC-PLUS-ABCDEFGHIJK"));
        assertNull(ActivationCodeFormat.validateOrNull(""));
        assertNull(ActivationCodeFormat.validateOrNull(null));
    }

    @Test
    void validationDoesNotCheckPackageExistence() {
        // "7K3M9QX2RR" có checksum hợp lệ (ký tự cuối R) nhưng gói "XX" không tồn tại —
        // validateOrNull chỉ kiểm TRA ĐỊNH DẠNG + checksum. Tồn tại gói do tầng nghiệp vụ
        // tra package_plan (p8 ACTIVATION_CODE_INVALID), không phải ở đây.
        assertEquals("XX-PLUS-7K3M9QX2RR", ActivationCodeFormat.validateOrNull("XX-PLUS-7K3M9QX2RR"));
    }

    @Test
    void normalizeMapsConfusableCharactersBeforeHashing() {
        // Mã gõ nhầm O thành 0 (hoặc I/L thành 1) phải chuẩn hoá về cùng một giá trị băm.
        String code = ActivationCodeFormat.generate("PLUS", random);
        String body = code.substring(code.lastIndexOf('-') + 1);
        if (body.contains("0")) {
            String typedWithLetterO = code.replace('0', 'O');
            assertEquals(code, ActivationCodeFormat.normalize(typedWithLetterO));
        }
        if (body.contains("1")) {
            String typedWithLetterI = code.replace('1', 'I');
            assertEquals(code, ActivationCodeFormat.normalize(typedWithLetterI));
        }
    }

    @Test
    void normalizeTrimsAndUppercases() {
        // CARE_BOX không chứa I/L/O/U nên tiền tố giữ nguyên ký tự (không bị ánh xạ Crockford).
        assertEquals("CC-CARE_BOX-7K3M9QX2RT",
                ActivationCodeFormat.normalize("  cc-care_box-7k3m9qx2rt  "));
    }

    @Test
    void generatedCodesAreUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(codes.add(ActivationCodeFormat.generate("CARE_BOX", random)));
        }
    }

    @Test
    void codePrefixIsTruncatedToEightCharacters() {
        assertEquals("PLUS-", ActivationCodeFormat.codePrefixOf("PLUS"));
        // CARE_BOX- dài 9 ký tự nên bị cắt còn 8 (p5 §5.5).
        assertEquals("CARE_BOX", ActivationCodeFormat.codePrefixOf("CARE_BOX"));
    }
}
