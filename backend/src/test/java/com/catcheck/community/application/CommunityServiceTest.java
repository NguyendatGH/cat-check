package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import com.catcheck.community.domain.CommunityRateLimitRule;
import com.catcheck.community.domain.port.CommunityRateLimiter;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.community.infrastructure.ratelimit.Bucket4jCommunityRateLimiter;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void reportingYourOwnPostIsRejectedAndNothingIsWritten() {
        UUID postId = UUID.randomUUID();
        CommunityRepository repository = mock(CommunityRepository.class);
        when(repository.findReportTargetAuthor(postId, null)).thenReturn(java.util.Optional.of(USER_ID));
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, allowingLimiter());

        assertThatThrownBy(() -> service.report(USER_ID, postId, null, "SPAM", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommunityErrorCode.REPORT_OWN_CONTENT));
        verify(repository, never()).report(any(), any(), any(), any(), any());
    }

    @Test
    void reportingYourOwnCommentIsRejectedAndNothingIsWritten() {
        UUID commentId = UUID.randomUUID();
        CommunityRepository repository = mock(CommunityRepository.class);
        when(repository.findReportTargetAuthor(null, commentId)).thenReturn(java.util.Optional.of(USER_ID));
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, allowingLimiter());

        assertThatThrownBy(() -> service.report(USER_ID, null, commentId, "SPAM", null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommunityErrorCode.REPORT_OWN_CONTENT));
        verify(repository, never()).report(any(), any(), any(), any(), any());
    }

    @Test
    void reportReasonOutsideWhitelistIsRejectedBeforeAnyWrite() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, allowingLimiter());

        for (String reason : java.util.Arrays.asList("spam", "ABUSE", "", null)) {
            assertThatThrownBy(() -> service.report(USER_ID, UUID.randomUUID(), null, reason, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(repository, never()).findReportTargetAuthor(any(), any());
        verify(repository, never()).report(any(), any(), any(), any(), any());
    }

    @Test
    void reportingSomeoneElsesPostStillWorks() {
        UUID postId = UUID.randomUUID();
        CommunityRepository repository = mock(CommunityRepository.class);
        when(repository.findReportTargetAuthor(postId, null)).thenReturn(java.util.Optional.of(UUID.randomUUID()));
        when(repository.report(eq(USER_ID), eq(postId), eq(null), eq("SPAM"), eq(null))).thenReturn(true);
        CommunityService service = service(repository, id -> AccountStatus.ACTIVE, allowingLimiter());

        service.report(USER_ID, postId, null, "SPAM", null);

        verify(repository).report(USER_ID, postId, null, "SPAM", null);
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
