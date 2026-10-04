package com.catcheck.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * p15 REQ-RBAC-01 — hai ví dụ trong đặc tả là hợp đồng, không phải minh hoạ:
 * {@code ngu***@gmail.com} và {@code 090****567}.
 */
class PiiMaskTest {

    @Test
    @DisplayName("Hai ví dụ nguyên văn của p15 REQ-RBAC-01")
    void matchesSpecExamples() {
        assertThat(PiiMask.email("nguyenvana@gmail.com")).isEqualTo("ngu***@gmail.com");
        assertThat(PiiMask.phone("0901234567")).isEqualTo("090****567");
    }

    @ParameterizedTest
    @CsvSource({
            "a@x.vn,          ***@x.vn",
            "ab@x.vn,         ***@x.vn",
            "abc@x.vn,        ***@x.vn",
            "abcd@x.vn,       abc***@x.vn",
            "a.b+tag@mail.co, a.b***@mail.co",
    })
    @DisplayName("Phần cục bộ <= 3 ký tự bị che TOÀN BỘ — giữ lại gần hết thì mask vô nghĩa")
    void masksShortLocalPartCompletely(String input, String expected) {
        assertThat(PiiMask.email(input)).isEqualTo(expected.strip());
    }

    @Test
    @DisplayName("Email không có @ hoặc rỗng: không bao giờ trả lại nguyên văn")
    void handlesMalformedEmail() {
        assertThat(PiiMask.email("khong-phai-email")).isEqualTo("***");
        assertThat(PiiMask.email("@domain.vn")).isEqualTo("***");
        assertThat(PiiMask.email("local@")).isEqualTo("***");
        assertThat(PiiMask.email(null)).isNull();
        assertThat(PiiMask.email("  ")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "123456,        ***",
            "1234567,       123*567",
            "+84901234567,  +84******567",
    })
    @DisplayName("Số <= 6 chữ số che hết; dài hơn thì giữ 3 đầu + 3 cuối")
    void masksPhone(String input, String expected) {
        assertThat(PiiMask.phone(input)).isEqualTo(expected.strip());
    }

    @Test
    @DisplayName("IP: giữ phần mạng, che octet/nhóm cuối (Nghị định 13 coi IP là dữ liệu cá nhân)")
    void masksIpAddress() {
        assertThat(PiiMask.ipAddress("203.0.113.42")).isEqualTo("203.0.113.*");
        assertThat(PiiMask.ipAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348"))
                .isEqualTo("2001:db8:85a3:*");
        assertThat(PiiMask.ipAddress("::1")).isEqualTo("***");
        assertThat(PiiMask.ipAddress("localhost")).isEqualTo("***");
        assertThat(PiiMask.ipAddress(null)).isNull();
    }
}
