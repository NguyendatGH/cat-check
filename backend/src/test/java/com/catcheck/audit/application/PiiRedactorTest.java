package com.catcheck.audit.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    private static final String SCAN_ID = "0199c7a2-5f3e-7b10-9c4d-1234567890ab";

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void uuidStringsStayBecauseAuditMustRecordIds() {
        Map<String, Object> redacted = redactor.redact(Map.of(
                "scanId", SCAN_ID,
                "dedupeKeyPrefix", "broadcast:" + SCAN_ID));

        assertThat(redacted)
                .containsEntry("scanId", SCAN_ID)
                .containsEntry("dedupeKeyPrefix", "broadcast:" + SCAN_ID);
    }

    @Test
    void piiAroundAUuidIsStillRedacted() {
        Map<String, Object> redacted = redactor.redact(Map.of(
                "reason", "user a.person@example.com scan " + SCAN_ID + " phone 0912345678",
                "blob", "x".repeat(40)));

        assertThat(redacted.get("reason"))
                .isEqualTo("user [REDACTED]_EMAIL scan " + SCAN_ID + " phone [REDACTED]_DIGITS");
        assertThat(redacted).containsEntry("blob", "[REDACTED]_BLOB");
    }
}
