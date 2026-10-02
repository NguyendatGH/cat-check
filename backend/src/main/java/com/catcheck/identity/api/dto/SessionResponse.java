package com.catcheck.identity.api.dto;

import java.util.List;

/** A4/A5/A7 — trạng thái phiên hiện tại (p8 §8.4.4 nhóm A). */
public record SessionResponse(
        boolean authenticated,
        SessionUser user,
        List<String> roles,
        SessionMfa mfa,
        String serverTime) {
}
