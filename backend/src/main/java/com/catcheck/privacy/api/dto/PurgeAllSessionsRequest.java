package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Body L62 — {@code reason} và {@code incidentId} đều <b>bắt buộc</b> (p8 §8.4.12 ô L62:
 * "{@code Rsn} (kèm {@code incidentId}）"). Hành động này đăng xuất mọi người dùng của hệ
 * thống; không có hồ sơ sự cố thì không có căn cứ (p11 §11.13.4 đòi audit kèm
 * {@code incidentId}).
 */
public record PurgeAllSessionsRequest(
        @NotNull UUID incidentId,
        @NotNull @Size(max = 2000) String reason) {
}
