package com.catcheck.identity.application.privacy;

import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class PrivacyUserAccountAdapter implements UserAccountPort {

    private final UserAccountRepository accountRepository;
    private final UserRoleRepository roleRepository;
    private final AuthenticatedSessionRevoker sessionRevoker;
    private final Clock clock;

    PrivacyUserAccountAdapter(
            UserAccountRepository accountRepository,
            UserRoleRepository roleRepository,
            AuthenticatedSessionRevoker sessionRevoker,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.sessionRevoker = sessionRevoker;
        this.clock = clock;
    }

    @Override
    public UserAccountSnapshot snapshot(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("app_user khong ton tai: " + userId));
        Set<String> roles = roleRepository.findByUserId(userId).stream()
                .map(UserRole::name)
                .collect(Collectors.toUnmodifiableSet());
        return new UserAccountSnapshot(
                account.id(),
                account.email().value(),
                account.status().name(),
                account.processingRestrictedAt(),
                account.deletionScheduledAt(),
                account.anonymizedAt(),
                account.pseudonymId(),
                roles);
    }

    @Override
    @Transactional
    public void requestAccountDeletion(UUID userId, Instant scheduledAt) {
        accountRepository.updateRestrictionState(userId, UserStatus.DELETION_REQUESTED, null, scheduledAt, null);
        sessionRevoker.revokeAllByUserId(userId);
    }

    @Override
    @Transactional
    public void cancelAccountDeletion(UUID userId) {
        accountRepository.updateRestrictionState(userId, UserStatus.ACTIVE, null, null, null);
    }

    @Override
    @Transactional
    public void setProcessingRestricted(UUID userId) {
        accountRepository.updateRestrictionState(userId, UserStatus.RESTRICTED, clock.instant(), null, null);
    }

    @Override
    @Transactional
    public void liftProcessingRestriction(UUID userId) {
        accountRepository.updateRestrictionState(userId, UserStatus.ACTIVE, null, null, null);
    }
}
