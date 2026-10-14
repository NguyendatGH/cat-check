package com.catcheck.admin.api;

import com.catcheck.admin.application.AdminJobControlService;
import com.catcheck.admin.application.AdminMaintenanceService;
import com.catcheck.admin.application.AdminNotificationOpsService;
import com.catcheck.admin.application.AdminOpsQueryService;
import com.catcheck.admin.application.AdminSettingsService;
import com.catcheck.admin.domain.AdminMetrics;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOpsControllerTest {

    private final AdminOpsQueryService opsQueryService = Mockito.mock(AdminOpsQueryService.class);
    private final AdminMaintenanceService maintenanceService = Mockito.mock(AdminMaintenanceService.class);
    private final AdminJobControlService jobControlService = Mockito.mock(AdminJobControlService.class);
    private final AdminNotificationOpsService notificationOpsService = Mockito.mock(AdminNotificationOpsService.class);
    private final AdminSettingsService settingsService = Mockito.mock(AdminSettingsService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = AdminApiMockMvc.build(new AdminOpsController(
                opsQueryService, maintenanceService, jobControlService, notificationOpsService, settingsService));
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
        Mockito.reset(opsQueryService, maintenanceService, jobControlService, notificationOpsService, settingsService);
    }

    @Test
    void dashboardMetricsRejectsOnlyNonAdminRoles() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("MODERATOR"));
        Mockito.when(opsQueryService.metrics()).thenReturn(new AdminMetrics(1, 2, 3, 4, 5));

        mockMvc.perform(get("/api/v1/admin/metrics/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        Mockito.verifyNoInteractions(opsQueryService);
    }

    @Test
    void dashboardMetricsAllowsAdminSupportRole() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPPORT"));
        Mockito.when(opsQueryService.metrics()).thenReturn(new AdminMetrics(10, 20, 30, 0, 1));

        mockMvc.perform(get("/api/v1/admin/metrics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").value(10));
    }

    @Test
    void dashboardMetricsAllowsAdminCatalogRole() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));
        Mockito.when(opsQueryService.metrics()).thenReturn(new AdminMetrics(7, 8, 9, 0, 0));

        mockMvc.perform(get("/api/v1/admin/metrics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").value(7));
    }

    @Test
    void dashboardMetricsStillAllowsAdminSuperAndDpo() throws Exception {
        Mockito.when(opsQueryService.metrics()).thenReturn(new AdminMetrics(1, 1, 1, 1, 1));

        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        mockMvc.perform(get("/api/v1/admin/metrics/dashboard")).andExpect(status().isOk());

        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));
        mockMvc.perform(get("/api/v1/admin/metrics/dashboard")).andExpect(status().isOk());
    }

    @Test
    void userStatusSummaryReturnsTotalsForUserReadRolesOnly() throws Exception {
        Mockito.when(opsQueryService.userStatusCounts())
                .thenReturn(java.util.Map.of("ACTIVE", 13L, "LOCKED", 2L));

        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPPORT"));
        mockMvc.perform(get("/api/v1/admin/users/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(13))
                .andExpect(jsonPath("$.byStatus.LOCKED").value(2));

        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));
        mockMvc.perform(get("/api/v1/admin/users/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void maintenanceWithAReasonShorterThanTenCharactersIsRejectedBeforeAnyUpdate() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(put("/api/v1/admin/system/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"reason\":\"ngan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REASON_REQUIRED"));

        Mockito.verifyNoInteractions(maintenanceService);
    }

    @Test
    void maintenanceWithAProperReasonUpdatesAndStripsIt() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.when(maintenanceService.update(
                        eq(true), any(), any(), anyString(), eq("Bao tri dinh ky cuoi tuan"),
                        any(), any(), any()))
                .thenReturn(new AdminMaintenanceService.Result("true", "", Instant.parse("2026-10-09T00:00:00Z")));

        mockMvc.perform(put("/api/v1/admin/system/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true,\"reason\":\"  Bao tri dinh ky cuoi tuan  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        Mockito.verify(maintenanceService).update(
                eq(true), any(), any(), anyString(), eq("Bao tri dinh ky cuoi tuan"), any(), any(), any());
    }

    @Test
    void liveBroadcastRequiresExplicitConfirmationBeforeItCanQueueNotifications() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(post("/api/v1/admin/system/broadcast")
                        .header("Idempotency-Key", "123e4567-e89b-42d3-a456-426614174000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"SYSTEM_MAINTENANCE\",\"title\":\"Bao tri\","
                                + "\"body\":\"Bao tri luc 23:00\","
                                + "\"reason\":\"Thong bao bao tri dinh ky cuoi tuan\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BROADCAST_CONFIRMATION_REQUIRED"));

        Mockito.verifyNoInteractions(notificationOpsService);
    }

    @Test
    void confirmedLiveBroadcastIsQueued() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.when(notificationOpsService.broadcast(anyString(), anyString(), anyString(), any(), eq(false),
                        any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new com.catcheck.notification.api.SystemBroadcastGateway.BroadcastResult(3, 3, "prefix"));

        mockMvc.perform(post("/api/v1/admin/system/broadcast")
                        .header("Idempotency-Key", "123e4567-e89b-42d3-a456-426614174002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"SYSTEM_MAINTENANCE\",\"title\":\"Bao tri\","
                                + "\"body\":\"Bao tri luc 23:00\",\"confirmed\":true,"
                                + "\"reason\":\"Thong bao bao tri dinh ky cuoi tuan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.notificationsQueued").value(3));
    }

    @Test
    void dryRunDoesNotRequireConfirmation() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.when(notificationOpsService.broadcast(anyString(), anyString(), anyString(), any(), eq(true),
                        any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new com.catcheck.notification.api.SystemBroadcastGateway.BroadcastResult(3, 0, "prefix"));

        mockMvc.perform(post("/api/v1/admin/system/broadcast")
                        .header("Idempotency-Key", "123e4567-e89b-42d3-a456-426614174001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"SYSTEM_MAINTENANCE\",\"title\":\"Bao tri\","
                                + "\"body\":\"Bao tri luc 23:00\",\"dryRun\":true,"
                                + "\"reason\":\"Thong bao bao tri dinh ky cuoi tuan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.notificationsQueued").value(0));
    }
}
