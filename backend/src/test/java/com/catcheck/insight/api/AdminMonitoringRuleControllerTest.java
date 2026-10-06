package com.catcheck.insight.api;

import com.catcheck.insight.application.MonitoringRuleAdminService;
import com.catcheck.insight.domain.HealthFlagSeverity;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.MonitoringRuleCode;
import com.catcheck.insight.domain.MonitoringRuleFixtures;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L38–L39 ở tầng api.
 *
 * <p>Ba nhánh quan trọng nhất: {@code ADMIN_CATALOG} đọc được nhưng KHÔNG ghi được (p8 L39 chỉ
 * cho {@code ADMIN_SUPER}), thiếu {@code If-Match} ⇒ {@code 428}, lệch {@code If-Match} ⇒
 * {@code 412} (p8 §8.1.11).</p>
 */
class AdminMonitoringRuleControllerTest {

    private static final String CODE = MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name();
    private static final Instant UPDATED_AT = Instant.parse("2026-10-06T09:00:00Z");
    /** {@code W/"<updatedAt epoch ms>-<khoá>"} — đúng định dạng của {@code shared.api.AdminETag}. */
    private static final String ETAG = "W/\"" + UPDATED_AT.toEpochMilli() + "-" + CODE + "\"";

    private final MonitoringRuleAdminService adminService = Mockito.mock(MonitoringRuleAdminService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = AdminApiMockMvc.build(new AdminMonitoringRuleController(adminService));
        Mockito.when(adminService.require(CODE)).thenReturn(rule());
        Mockito.when(adminService.listForAdmin()).thenReturn(List.of(rule()));
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    @Test
    void catalogRoleCanReadTheRuleList() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));

        mockMvc.perform(get("/api/v1/admin/monitoring-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(CODE))
                .andExpect(jsonPath("$.items[0].etag").value(ETAG))
                .andExpect(jsonPath("$.items[0].params.windowHours").value(72));
    }

    @Test
    void supportRoleCannotEvenReadTheRuleList() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPPORT"));

        mockMvc.perform(get("/api/v1/admin/monitoring-rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void catalogRoleCannotWriteBecauseLThirtyNineIsSuperOnly() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));

        mockMvc.perform(patch("/api/v1/admin/monitoring-rules/{code}", CODE)
                        .header("If-Match", ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"Tat tam khi hieu chuan lai\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        Mockito.verify(adminService, Mockito.never())
                .update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingIfMatchIsPreconditionRequired() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(patch("/api/v1/admin/monitoring-rules/{code}", CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"Tat tam khi hieu chuan lai\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.errorCode").value("PRECONDITION_REQUIRED"));

        Mockito.verify(adminService, Mockito.never())
                .update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleIfMatchIsResourceModified() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(patch("/api/v1/admin/monitoring-rules/{code}", CODE)
                        .header("If-Match", "W/\"0-" + CODE + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"Tat tam khi hieu chuan lai\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_MODIFIED"));
    }

    @Test
    void superRoleWithAMatchingIfMatchUpdatesTheRuleAndReturnsTheNewEtag() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Instant savedAt = Instant.parse("2026-10-06T10:00:00Z");
        MonitoringRule after = MonitoringRuleFixtures.rule(CODE, Map.of("windowHours", 48),
                HealthFlagSeverity.ATTENTION, 24, "insight.flag.x", savedAt);
        after.applyAdminUpdate(false, null, null, null, savedAt);
        Mockito.when(adminService.update(eq(CODE), eq(false), any(), eq(24), any(), any()))
                .thenReturn(after);

        mockMvc.perform(patch("/api/v1/admin/monitoring-rules/{code}", CODE)
                        .header("If-Match", ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"cooldownHours\":24,"
                                + "\"params\":{\"windowHours\":48},\"reason\":\"Tat tam khi hieu chuan lai\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag",
                        "W/\"" + Instant.parse("2026-10-06T10:00:00Z").toEpochMilli() + "-" + CODE + "\""))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.cooldownHours").value(24));
    }

    @Test
    void reasonShorterThanTenCharactersIsRejectedByBeanValidation() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(patch("/api/v1/admin/monitoring-rules/{code}", CODE)
                        .header("If-Match", ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"reason\":\"ngan\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Thiếu một field kiểu bọc KHÔNG được thành {@code 500} (handoff H15.99): với {@code record},
     * Jackson truyền {@code null} cho property vắng mặt và kiểu nguyên thuỷ sẽ ném ngay ở bước
     * bind — trước cả khi controller kịp kiểm {@code If-Match}.
     */
    @Test
    void aBodyWithOnlyReasonBindsCleanlyInsteadOfFailingOnNullPrimitives() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.when(adminService.update(eq(CODE), any(), any(), any(), any(), any()))
                .thenReturn(rule());

        mockMvc.perform(patch("/api/v1/admin/monitoring-rules/{code}", CODE)
                        .header("If-Match", ETAG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Chi doi mot truong duy nhat\"}"))
                .andExpect(status().isOk());
    }

    private static MonitoringRule rule() {
        return MonitoringRuleFixtures.rule(CODE, Map.of("windowHours", 72, "minCount", 2),
                HealthFlagSeverity.ATTENTION, 72, "insight.flag.repeated_out_of_range", UPDATED_AT);
    }
}
