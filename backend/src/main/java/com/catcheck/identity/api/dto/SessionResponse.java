package com.catcheck.identity.api.dto;

import java.util.List;

public record SessionResponse(
        boolean authenticated,
        SessionUser user,
        List<String> roles,
        SessionMfa mfa,
        String serverTime) {
}
