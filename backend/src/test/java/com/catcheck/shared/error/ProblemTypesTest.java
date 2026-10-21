package com.catcheck.shared.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemTypesTest {

    @Test
    void normalize_fallsBackToRelativeBaseAndAddsTrailingSlash() {
        assertThat(ProblemTypes.normalize(null)).isEqualTo("/problems/");
        assertThat(ProblemTypes.normalize("  ")).isEqualTo("/problems/");
        assertThat(ProblemTypes.normalize(" https://example.com/problems ")).isEqualTo("https://example.com/problems/");
        assertThat(ProblemTypes.normalize("https://example.com/problems/")).isEqualTo("https://example.com/problems/");
    }
}
