package com.catcheck.shared.testing;

import com.catcheck.shared.security.MfaLevel;
import com.catcheck.shared.security.SecurityPrincipal;

import java.util.Set;
import java.util.UUID;

/**
 * {@link SecurityPrincipal} tối giản cho test tầng api.
 *
 * <p>Tồn tại vì {@code identity.application.AuthPrincipal} (bản cài đặt thật) kéo theo cả
 * module identity; test phân quyền chỉ cần đúng ba thứ: id, email, tập vai trò.</p>
 */
public record TestPrincipal(UUID userId, String email, Set<String> roles) implements SecurityPrincipal {

    public static TestPrincipal withRoles(String... roles) {
        return new TestPrincipal(UUID.randomUUID(), "admin@catcheck.vn", Set.of(roles));
    }

    @Override
    public MfaLevel mfaLevel() {
        // Mọi /admin/** đã qua AdminMfaGateFilter trước khi tới controller (p8 §8.4.12 điều
        // kiện chung #1), nên principal trong test tầng api phải đã đạt TOTP.
        return MfaLevel.TOTP;
    }
}
