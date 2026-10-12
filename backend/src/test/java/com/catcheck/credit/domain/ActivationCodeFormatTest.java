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
        assertEquals("XX-PLUS-7K3M9QX2RR", ActivationCodeFormat.validateOrNull("XX-PLUS-7K3M9QX2RR"));
    }

    @Test
    void normalizeMapsConfusableCharactersBeforeHashing() {
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
        assertEquals("CARE_BOX", ActivationCodeFormat.codePrefixOf("CARE_BOX"));
    }
}
