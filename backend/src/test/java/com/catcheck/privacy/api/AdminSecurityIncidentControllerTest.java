package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.OpenSecurityIncidentRequest;
import com.catcheck.privacy.api.dto.UpdateSecurityIncidentRequest;
import com.catcheck.privacy.application.SecurityIncidentService;
import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L59/L60/L61 — ô {@code Q27}: <b>{@code DPO} ghi, {@code ADMIN_SUPER} đọc</b>.
 *
 * <p>Đây là endpoint duy nhất của gói này có hai mức quyền khác nhau trên cùng một tài nguyên,
 * nên hai nhánh phải được kiểm riêng: {@code ADMIN_SUPER} đọc được danh sách nhưng không mở và
 * không sửa được hồ sơ.</p>
 */
class AdminSecurityIncidentControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID ACTOR = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6660");
    private static final UUID INCIDENT_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6661");
    private static final String GOOD_REASON = "Mo ho so theo quy trinh 15.9.5";

    private final SecurityIncidentService incidentService = mock(SecurityIncidentService.class);
    private final AdminSecurityIncidentController controller =
            new AdminSecurityIncidentController(incidentService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void adminSuperMayReadTheIncidentQueue() {
        when(incidentService.list(any(), any(), anyInt(), anyInt())).thenReturn(List.of(incident()));
        when(incidentService.count(any(), any())).thenReturn(1L);

        var page = controller.list(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"), null, null, 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().authorityDeadline())
                .isEqualTo(NOW.minusSeconds(3600).plusSeconds(72 * 3600));
    }

    @Test
    void supportMayNotReadTheIncidentQueue() {
        assertThatThrownBy(() -> controller.list(
                AdminTestPrincipal.of(ACTOR, "ADMIN_SUPPORT"), null, null, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void adminSuperMayNotOpenAnIncident() {
        assertThatThrownBy(() -> controller.open(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"),
                openRequest("HIGH"), new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(incidentService, never()).open(any(), any(), any());
    }

    @Test
    void adminSuperMayNotPatchAnIncident() {
        assertThatThrownBy(() -> controller.update(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"),
                INCIDENT_ID, updateRequest(), new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(incidentService, never()).patch(any(), any(), any(), any());
    }

    @Test
    void dpoOpensAnIncidentAndTheEnumsAreParsedToDomainValues() {
        when(incidentService.open(eq(ACTOR), any(), any())).thenReturn(incident());

        controller.open(AdminTestPrincipal.of(ACTOR, "DPO"), openRequest("critical"),
                new MockHttpServletRequest());

        ArgumentCaptor<SecurityIncidentService.Command> command =
                ArgumentCaptor.forClass(SecurityIncidentService.Command.class);
        verify(incidentService).open(eq(ACTOR), command.capture(), any());
        assertThat(command.getValue().severity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(command.getValue().category()).isEqualTo(IncidentCategory.DATA_BREACH);
        assertThat(command.getValue().reason()).isEqualTo(GOOD_REASON);
    }

    @Test
    void anUnknownSeverityIs400NotAnEnumCrash() {
        assertThatThrownBy(() -> controller.open(AdminTestPrincipal.of(ACTOR, "DPO"),
                openRequest("CATASTROPHIC"), new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.VALIDATION_FAILED);
    }

    @Test
    void dpoPatchesOnlyTheFieldsPresentInTheBody() {
        when(incidentService.patch(eq(ACTOR), eq(INCIDENT_ID), any(), any())).thenReturn(incident());

        controller.update(AdminTestPrincipal.of(ACTOR, "DPO"), INCIDENT_ID, updateRequest(),
                new MockHttpServletRequest());

        ArgumentCaptor<SecurityIncidentService.Patch> patch =
                ArgumentCaptor.forClass(SecurityIncidentService.Patch.class);
        verify(incidentService).patch(eq(ACTOR), eq(INCIDENT_ID), patch.capture(), any());
        assertThat(patch.getValue().severity()).isNull();
        assertThat(patch.getValue().category()).isNull();
        assertThat(patch.getValue().authorityNotifiedAt()).isEqualTo(NOW.minusSeconds(600));
    }

    private static OpenSecurityIncidentRequest openRequest(String severity) {
        return new OpenSecurityIncidentRequest(severity, "DATA_BREACH",
                "Ro bang spring_session qua ban sao luu", 120, List.of("D1"),
                NOW.minusSeconds(3600), null, GOOD_REASON);
    }

    private static UpdateSecurityIncidentRequest updateRequest() {
        return new UpdateSecurityIncidentRequest(null, null, null, null, null, null, null,
                NOW.minusSeconds(600), null, null, null, "Da bao A05 theo mau so 08");
    }

    private static SecurityIncident incident() {
        return new SecurityIncident(INCIDENT_ID, "INC-2026-000001", IncidentSeverity.CRITICAL,
                IncidentCategory.DATA_BREACH, "Ro DB", 120, List.of("D1"), NOW.minusSeconds(3600),
                null, null, null, null, null, null, ACTOR, null, NOW.minusSeconds(3600));
    }
}
