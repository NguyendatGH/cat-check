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

/**
 * Adapter A1 → A2: implement {@link UserAccountPort} (named interface {@code privacy::spi},
 * đã khai trong {@code identity/package-info.java} — xem {@code docs/handovers/A1-A2-integration.md}).
 *
 * <p>Đặt trong {@code identity.application.privacy} theo đúng khuôn mà phía privacy dùng cho
 * SPI ngược lại của chính nó ({@code com.catcheck.privacy.application.privacy.PrivacyErasureParticipant}
 * implement {@code privacy.spi.ErasureParticipant}) — dù ArchUnit R10 chỉ bắt buộc vị trí này cho
 * {@code ErasureParticipant}, đặt cùng chỗ cho mọi adapter "participant trong cơ chế của module khác"
 * giữ codebase nhất quán và dễ tìm.</p>
 *
 * <p><b>Không thêm SQL mới.</b> Uỷ quyền toàn bộ cho {@link UserAccountRepository} và
 * {@link UserRoleRepository} đã có sẵn của chính identity (đọc/ghi {@code app_user} /
 * {@code user_role}, V5__identity.sql) — một nguồn sự thật duy nhất cho hai bảng đó, tránh
 * một câu SQL thứ hai lặp lại logic map cột đã có trong {@code JdbcUserAccountRepository}.</p>
 */
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

    /**
     * C9 (p8): {@code status} → {@code DELETION_REQUESTED} + {@code deletion_scheduled_at}.
     * {@code processing_restricted_at} PHẢI về NULL dù tài khoản đang RESTRICTED hay không —
     * CHECK {@code ck_app_user_restricted_pair} (V5__identity.sql) chỉ cho phép non-null khi
     * {@code status = 'RESTRICTED'}, và diagram chuyển trạng thái của p4 §A1 cho phép cả
     * RESTRICTED → DELETION_REQUESTED.
     *
     * <p>Thu hồi toàn bộ phiên ngay: p4 §A1 (bảng chuyển trạng thái "* → DELETION_REQUESTED")
     * yêu cầu "thu hồi toàn bộ phiên, chặn đăng nhập" tức thời — javadoc gốc của
     * {@link UserAccountPort} ghi rõ đây là việc identity phải làm khi implement cổng này.</p>
     */
    @Override
    @Transactional
    public void requestAccountDeletion(UUID userId, Instant scheduledAt) {
        accountRepository.updateRestrictionState(userId, UserStatus.DELETION_REQUESTED, null, scheduledAt, null);
        sessionRevoker.revokeAllByUserId(userId);
    }

    /** C10 (p8): về {@code ACTIVE}, xoá {@code deletion_scheduled_at} (cũng gọi từ link email). */
    @Override
    @Transactional
    public void cancelAccountDeletion(UUID userId) {
        accountRepository.updateRestrictionState(userId, UserStatus.ACTIVE, null, null, null);
    }

    /** C11 (p8, p15 §15.4.7): {@code RESTRICTED} + {@code processing_restricted_at = now()}. */
    @Override
    @Transactional
    public void setProcessingRestricted(UUID userId) {
        accountRepository.updateRestrictionState(userId, UserStatus.RESTRICTED, clock.instant(), null, null);
    }

    /** C12 (p8): về {@code ACTIVE}, xoá {@code processing_restricted_at}. */
    @Override
    @Transactional
    public void liftProcessingRestriction(UUID userId) {
        accountRepository.updateRestrictionState(userId, UserStatus.ACTIVE, null, null, null);
    }
}
