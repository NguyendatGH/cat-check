package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import com.catcheck.community.domain.CommunityRateLimitRule;
import com.catcheck.community.domain.port.CommunityRateLimiter;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.community.infrastructure.ratelimit.Bucket4jCommunityRateLimiter;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CommunityServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void restrictedAccountCannotUseAnyCommunityWritePath() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityService service = service(repository, id -> AccountStatus.RESTRICTED, allowingLimiter());

        assertRestricted(() -> service.createPost(USER_ID, "TIP", "Title", "Body", List.of()));
        assertRestricted(() -> service.createComment(USER_ID, UUID.randomUUID(), "Body"));
        assertRestricted(() -> service.reaction(USER_ID, UUID.randomUUID(), "LIKE", true));
        assertRestricted(() -> service.report(USER_ID, UUID.randomUUID(), null, "SPAM", null));
        verifyNoInteractions(repository);
    }

    @Test
    void eachWriteFlowReturnsItsOwnRateLimitCode() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, rejectingLimiter());

        assertThat(assertRateLimited(() -> service.createPost(USER_ID, "TIP", "Title", "Body", List.of()),
                CommunityErrorCode.POST_RATE_LIMITED).retryAfterSeconds()).isEqualTo(12L);
        assertThat(assertRateLimited(() -> service.createComment(USER_ID, UUID.randomUUID(), "Body"),
                CommunityErrorCode.COMMENT_RATE_LIMITED).retryAfterSeconds()).isEqualTo(12L);
        assertThat(assertRateLimited(() -> service.report(USER_ID, UUID.randomUUID(), null, "SPAM", null),
                CommunityErrorCode.REPORT_RATE_LIMITED).retryAfterSeconds()).isEqualTo(12L);
        verifyNoInteractions(repository);
    }

    @Test
    void reactionIsNotRateLimited() {
        CommunityRepository repository = mock(CommunityRepository.class);
        org.mockito.Mockito.when(repository.findPost(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(mock(com.catcheck.community.domain.CommunityPost.class)));
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, rejectingLimiter());

        // Reaction là toggle idempotent, không có luật rate limit — limiter từ chối cũng không chặn.
        service.reaction(USER_ID, UUID.randomUUID(), "LIKE", true);
    }

    @Test
    void postRateLimitConsumesTheConfiguredNumberOfRequestsForOneUser() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, new Bucket4jCommunityRateLimiter());

        for (int i = 0; i < 5; i++) {
            service.createPost(USER_ID, "TIP", "Title", "Body", List.of());
        }

        RateLimitedException ex = assertRateLimited(
                () -> service.createPost(USER_ID, "TIP", "Title", "Body", List.of()), CommunityErrorCode.POST_RATE_LIMITED);
        assertThat(ex.retryAfterSeconds()).isBetween(1L, 600L);
    }

    private static CommunityService service(
            CommunityRepository repository, UserAccountPort accountPort, CommunityRateLimiter rateLimiter
    ) {
        return new CommunityService(repository, accountPort, rateLimiter);
    }

    private static CommunityRateLimiter allowingLimiter() {
        return new CommunityRateLimiter() {
            @Override public boolean tryConsume(CommunityRateLimitRule rule, String subject) { return true; }
            @Override public long secondsUntilRefill(CommunityRateLimitRule rule, String subject) { return 0; }
        };
    }

    private static CommunityRateLimiter rejectingLimiter() {
        return new CommunityRateLimiter() {
            @Override public boolean tryConsume(CommunityRateLimitRule rule, String subject) { return false; }
            @Override public long secondsUntilRefill(CommunityRateLimitRule rule, String subject) { return 12; }
        };
    }

    private static void assertRestricted(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(PermissionDeniedException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(CommunityErrorCode.ACCOUNT_RESTRICTED));
    }

    private static RateLimitedException assertRateLimited(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action, CommunityErrorCode code
    ) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);
        assertThat(thrown).isInstanceOfSatisfying(RateLimitedException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(code));
        return (RateLimitedException) thrown;
    }
}
