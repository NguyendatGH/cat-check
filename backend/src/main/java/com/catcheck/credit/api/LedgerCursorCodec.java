package com.catcheck.credit.api;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerCursor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class LedgerCursorCodec {

    private static final String SEPARATOR = ".";

    private LedgerCursorCodec() {
    }

    static String encode(LedgerCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = cursor.sortKey().toEpochMilli() + SEPARATOR + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static LedgerCursor parse(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0) {
                return null;
            }
            return new LedgerCursor(
                    Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return null;
        }
    }
}
