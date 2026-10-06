package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.PurgeAllSessionsRequest;
import com.catcheck.privacy.application.GlobalSessionPurgeService;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.AdminApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L62 — {@code R:ADMIN_SUPER} + {@code Rsn} kèm {@code incidentId} (p8 §8.4.12).
 *
 * <p>Không bài nào ở đây chạm DB thật, có chủ đích: một lần bấm L62 thật sẽ đá văng mọi
 * phiên đang mở trên DB dùng chung. Phần SQL được chứng minh riêng ở
 * {@code GlobalSessionPurgeIntegrationTest} trên PostgreSQL Testcontainers với dữ liệu của
 * chính bài đó.</p>
 */
class AdminSecuritySessionControllerTest {

    private static final UUID ACTOR = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6650");
    private static final UUID INCIDENT_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6651");
    private static final String GOOD_REASON = "Ro bang spring_session, chay runbook R1";

    private final GlobalSessionPurgeService purgeService = mock(GlobalSessionPurgeService.class);
    private final AdminSecuritySessionController controller =
            new AdminSecuritySessionController(purgeService);

    @Test
    void dpoCannotPurgeEverySession() {
        assertThatThrownBy(() -> controller.purgeAll(AdminTestPrincipal.of(ACTOR, "DPO"),
                new PurgeAllSessionsRequest(INCIDENT_ID, GOOD_REASON), new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(purgeService, never()).purgeAll(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void supportCannotPurgeEverySessionEither() {
        assertThatThrownBy(() -> controller.purgeAll(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPPORT"),
                new PurgeAllSessionsRequest(INCIDENT_ID, GOOD_REASON), new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aShortReasonIsRefusedBeforeAnySessionDies() {
        assertThatThrownBy(() -> controller.purgeAll(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"),
                new PurgeAllSessionsRequest(INCIDENT_ID, "R1"), new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.REASON_REQUIRED);
        verify(purgeService, never()).purgeAll(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void adminSuperPurgesAndGetsTheRevokedCountBack() {
        when(purgeService.purgeAll(eq(ACTOR), eq("ADMIN_SUPER"), eq(INCIDENT_ID), eq(GOOD_REASON), any()))
                .thenReturn(new GlobalSessionPurgeService.Result("INC-2026-000001", 812, 804, 17, 3, 1_200L));

        var response = controller.purgeAll(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"),
                new PurgeAllSessionsRequest(INCIDENT_ID, GOOD_REASON), new MockHttpServletRequest());

        assertThat(response.revokedCount()).isEqualTo(812);
        assertThat(response.incidentRef()).isEqualTo("INC-2026-000001");
        assertThat(response.elapsedMillis())
                .as("mục tiêu p8 L62 / p11 §11.13.4: hoàn tất ≤ 5 phút")
                .isLessThan(GlobalSessionPurgeService.TARGET_DURATION.toMillis());
    }
}
