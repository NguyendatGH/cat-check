package com.catcheck.notification.api;

/**
 * Cong gui email. Trien khai that o {@code notification.infrastructure..}
 * (SMTP, hoac ghi file o local).
 */
public interface EmailSender {

    /**
     * Gui email. Khong nem loi khi that bai — thong bao se ghi vao outbox roi job
     * thu lai (p11 §11.2.1: API tra {@code 202} ngay, khong cho SMTP treo lam
     * hong luong dang ky).
     */
    void send(EmailMessage message);
}
