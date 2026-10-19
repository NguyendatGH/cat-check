package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.UserIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository {

    List<UserIdentity> findByUserId(UUID userId);

    Optional<UserIdentity> findLocal(UUID userId);

    Optional<UserIdentity> findByProviderSubject(IdentityProvider provider, String providerUserId);

    boolean existsByProviderSubject(IdentityProvider provider, String providerUserId);

    void insert(UserIdentity identity);

    void touchLastUsed(UUID identityId, Instant lastUsedAt);

    boolean delete(UUID identityId);

    int countByUserId(UUID userId);
}
