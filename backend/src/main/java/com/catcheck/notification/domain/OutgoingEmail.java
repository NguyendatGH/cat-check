package com.catcheck.notification.domain;

import java.util.Map;

/**
 * Email đã sẵn sàng giao cho transport.
 *
 * <p>Vì sao không dùng thẳng {@code notification.api.EmailMessage}: ArchUnit R1 cấm
 * {@code ..domain..} phụ thuộc {@code ..api..}. Cổng {@code EmailTransport} nằm ở
 * {@code domain.port} nên tham số của nó phải là type miền.</p>
 */
public record OutgoingEmail(String recipientEmail, String templateCode, String locale,
                            Map<String, Object> variables) {

    public OutgoingEmail {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        locale = (locale == null || locale.isBlank()) ? "vi" : locale;
    }
}
