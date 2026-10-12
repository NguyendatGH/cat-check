package com.catcheck.notification.domain;

import java.util.Map;

public record OutgoingEmail(String recipientEmail, String templateCode, String locale,
                            Map<String, Object> variables) {

    public OutgoingEmail {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        locale = (locale == null || locale.isBlank()) ? "vi" : locale;
    }
}
