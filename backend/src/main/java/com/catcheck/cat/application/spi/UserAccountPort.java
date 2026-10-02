package com.catcheck.cat.application.spi;

import java.util.UUID;

/**
 * Cổng đọc trạng thái tài khoản, do module {@code identity} hiện thực.
 *
 * <p>Module cat <b>không</b> tra cứu {@code app_user} trực tiếp. Lý do không phải là sợ phụ thuộc
 * kỹ thuật mà là vì p15/I19 buộc phải có đúng MỘT chỗ quyết định "tài khoản này có được xử lý không",
 * và chỗ đó phải nằm ở module sở hữu vòng đời tài khoản là identity.</p>
 */
public interface UserAccountPort {

    /**
     * @return trạng thái hiện tại, hoặc {@code null} nếu không có tài khoản với id này — trường hợp
     *         cuối cùng xảy ra khi dữ liệu bị xoá cứng theo yêu cầu bảo vệ dữ liệu
     */
    AccountStatus statusOf(UUID userId);

    /**
     * Day {@code app_user.onboarding_status} toi {@code milestone} — CHI TIEN, khong lui.
     *
     * <p>Bug that da sua: truoc day khong co ai cap nhat cot nay sau luc dang ky, no vinh vien
     * la {@code ACCOUNT_ONLY}. {@code CatCreatedEvent} co phat nhung KHONG co listener nao.
     * Hau qua o SPA: guard {@code RequireOnboarding} luon thay "chua xong onboarding" nen da
     * dang nhap xong van bi day nguoc ve {@code /onboarding/cat} vinh vien, khong bao gio vao
     * duoc app. Xac nhan that: tao ho so meo + nop khao sat xong, {@code GET /auth/session}
     * van tra {@code ACCOUNT_ONLY}.</p>
     *
     * <p>Lam bang loi goi truc tiep qua SPI (khong qua event) vi {@code identity} KHONG duoc
     * phep phu thuoc {@code cat} ({@code identity/package-info.java} chi cho
     * {@code shared, audit::api, notification::api, privacy::spi}) nen khong dat listener ben
     * identity duoc; chieu {@code cat -> identity} moi la chieu hop le.</p>
     */
    void advanceOnboardingStatus(UUID userId, OnboardingMilestone milestone);
}
