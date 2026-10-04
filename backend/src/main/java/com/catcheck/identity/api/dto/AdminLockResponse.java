package com.catcheck.identity.api.dto;

import com.catcheck.identity.application.AdminUserService;

import java.util.UUID;

/**
 * Kết quả L7 (khoá) / L8 (mở khoá).
 *
 * @param revokedSessions số phiên bị thu hồi — luôn 0 ở L8, vì mở khoá <b>không</b> tạo lại
 *                        phiên nào (p8 L8) và cũng không có gì để thu hồi
 */
public record AdminLockResponse(UUID userId, String status, int revokedSessions) {

    public static AdminLockResponse from(AdminUserService.LockResult result) {
        return new AdminLockResponse(result.userId(), result.status().name(), result.revokedSessions());
    }
}
