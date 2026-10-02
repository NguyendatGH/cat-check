package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.UserIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong vao {@code user_identity}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface UserIdentityRepository {

    List<UserIdentity> findByUserId(UUID userId);

    /** Tra ve identity LOCAL duy nhat cua tai khoan. */
    Optional<UserIdentity> findLocal(UUID userId);

    /**
     * Tim theo khoa {@code (provider, provider_user_id)} — dung cho callback OAuth
     * va cho quy tac "dung thuoc, khong tao tai khoan moi" khi email da ton tai.
     */
    Optional<UserIdentity> findByProviderSubject(IdentityProvider provider, String providerUserId);

    boolean existsByProviderSubject(IdentityProvider provider, String providerUserId);

    void insert(UserIdentity identity);

    void touchLastUsed(UUID identityId, Instant lastUsedAt);

    /** Giu lai LOCAL de doi mat khau duoc (p11 §11.4.4: chi duoc go khi con LOCAL). */
    boolean delete(UUID identityId);

    /** Dem identity con lai de chan {@code 409 IDENTITY_LAST_REMAINING} (p8 B10). */
    int countByUserId(UUID userId);
}
