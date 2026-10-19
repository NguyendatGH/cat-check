package com.catcheck.shared.api;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.AdminApiErrorCode;

import java.time.Instant;

public final class AdminETag {

    private AdminETag() {
    }

    public static String of(Instant updatedAt, String key) {
        long epochMilli = updatedAt == null ? 0L : updatedAt.toEpochMilli();
        return "W/\"" + epochMilli + "-" + key + "\"";
    }

    public static void requireMatch(String ifMatch, String expected) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessRuleException(AdminApiErrorCode.PRECONDITION_REQUIRED);
        }
        String normalized = ifMatch.strip();
        if (!normalized.equals(expected) && !normalized.equals("*")) {
            throw new BusinessRuleException(AdminApiErrorCode.RESOURCE_MODIFIED, expected);
        }
    }
}
