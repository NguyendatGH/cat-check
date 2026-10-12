package com.catcheck.notification.api;

import java.net.URI;
import java.util.Map;

public record EmailMessage(
        String recipientEmail,
        String templateKey,
        String locale,
        Map<String, Object> variables,
        URI replyTo) {

    public EmailMessage {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static EmailMessage of(String recipientEmail, String templateKey,
                                  String locale, Map<String, Object> variables) {
        return new EmailMessage(recipientEmail, templateKey, locale, variables, null);
    }
}
