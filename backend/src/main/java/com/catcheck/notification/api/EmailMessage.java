package com.catcheck.notification.api;

import java.net.URI;
import java.util.Map;

/**
 * Mot email can gui.
 *
 * <p>Khong dung {@code javax.mail.internet.MimeMessage} o day: do SMTP la chi tiet
 * ha tang, module goi khong nen bi phu thuoc vao no. {@code notification} se doi
 * sang implementation nao cung duoc.</p>
 *
 * @param recipientEmail dia chi nhan
 * @param templateKey    khoa template, vi du {@code "auth.otp.verify"} — noi dung lay
 *                       tu bang template, KHONG cat HTML vao code
 * @param locale         ngon ngu noi dung
 * @param variables      bien noi suy (trong do KHONG duoc co OTP tho neu can
 *                       che — xem {@code SecretVariable} neu trien khai day du)
 * @param replyTo        dia chi nhan cau hoi, {@code null} neu khong can
 */
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
