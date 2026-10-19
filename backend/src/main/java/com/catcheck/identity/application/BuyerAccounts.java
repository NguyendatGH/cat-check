package com.catcheck.identity.application;

import com.catcheck.identity.domain.UserRole;

import java.util.Collection;

/** Tài khoản người mua thuần: có vai trò USER và không mang vai trò staff nào. */
final class BuyerAccounts {

    private BuyerAccounts() {
    }

    static boolean isBuyerOnly(Collection<?> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        boolean hasUser = false;
        for (Object role : roles) {
            String name = role instanceof UserRole userRole ? userRole.name() : String.valueOf(role);
            if (!UserRole.USER.name().equals(name)) {
                return false;
            }
            hasUser = true;
        }
        return hasUser;
    }
}
