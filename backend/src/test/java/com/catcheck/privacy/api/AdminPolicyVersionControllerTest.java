package com.catcheck.privacy.api;

import com.catcheck.privacy.application.PolicyVersionAdminService;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L46–L48 ở tầng api.
 *
 * <p>Nhánh phân quyền quan trọng nhất của cả gói: <b>{@code ADMIN_SUPER} đọc được nhưng KHÔNG
 * publish được</b> — p14 §14.2.2 ô {@code Q22} ghi {@code ADMIN_SUPER = ❌} tường minh và
 * p11 §11.5.4 cho {@code DPO} là vai trò duy nhất "Đọc + publish" {@code policy_version}.</p>
 */
class AdminPolicyVersionControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final Instant NEXT_MONTH = NOW.plus(Duration.ofDays(30));
    private static final UUID DRAFT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    private final PolicyVersionAdminService adminService = Mockito.mock(PolicyVersionAdminService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = AdminApiMockMvc.build(new AdminPolicyVersionController(adminService));
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    @Test
    void superAdminCanListVersions() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.when(adminService.list(any(), any(), anyInt(), anyInt())).thenReturn(
                new PolicyVersionAdminService.AdminPolicyPage(List.of(draftRow()), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/v1/admin/policy-versions").param("policyType", "PRIVACY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].hasContent").value(true))
                .andExpect(jsonPath("$.items[0].contentHash").value("a".repeat(64)));
    }

    @Test
    void listIsForbiddenForCatalogRole() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));

        mockMvc.perform(get("/api/v1/admin/policy-versions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void draftingIsForbiddenForSuperAdminBecauseQTwentyTwoIsDpoOnly() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(post("/api/v1/admin/policy-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        Mockito.verifyNoInteractions(adminService);
    }

    @Test
    void dpoCanDraftAndGetsCreatedWithALocationHeader() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));
        Mockito.when(adminService.createDraft(eq(PolicyType.PRIVACY), eq("2.0"), eq("vi"), anyString(),
                        any(), any(), any(), eq(true), any(), eq(NEXT_MONTH), any()))
                .thenReturn(draftRow());

        mockMvc.perform(post("/api/v1/admin/policy-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/policy-versions/" + DRAFT_ID))
                .andExpect(jsonPath("$.requiresReconsent").value(true))
                .andExpect(jsonPath("$.affectedPurposes[0]").value("SERVICE_CORE"));
    }

    @Test
    void draftingWithAnUnknownPolicyTypeIsUnprocessableEntity() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));

        mockMvc.perform(post("/api/v1/admin/policy-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody().replace("\"PRIVACY\"", "\"KHONG_CO\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("POLICY_VERSION_INVALID"));
    }

    @Test
    void draftingWithAShortReasonIsRejectedByBeanValidation() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));

        mockMvc.perform(post("/api/v1/admin/policy-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftBody().replace("Cap nhat chinh sach theo ND356", "ngan")))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(adminService);
    }

    @Test
    void publishingIsForbiddenForSuperAdmin() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(post("/api/v1/admin/policy-versions/{id}/publish", DRAFT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Da ra soat phap ly xong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void dpoCanPublish() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));
        Mockito.when(adminService.publish(eq(DRAFT_ID), any(), any())).thenReturn(publishedRow());

        mockMvc.perform(post("/api/v1/admin/policy-versions/{id}/publish", DRAFT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Da ra soat phap ly xong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.publishedBy").isNotEmpty());
    }

    @Test
    void publishingTwiceSurfacesAlreadyPublishedAsConflict() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));
        Mockito.when(adminService.publish(eq(DRAFT_ID), any(), any())).thenThrow(
                new ConflictException(AdminPolicyErrorCode.POLICY_VERSION_ALREADY_PUBLISHED,
                        DRAFT_ID.toString()));

        mockMvc.perform(post("/api/v1/admin/policy-versions/{id}/publish", DRAFT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Da ra soat phap ly xong\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("POLICY_VERSION_ALREADY_PUBLISHED"));
    }

    // ------------------------------------------------------------------ fixtures

    private static String draftBody() {
        return "{\"policyType\":\"PRIVACY\",\"version\":\"2.0\",\"locale\":\"vi\","
                + "\"title\":\"Chinh sach quyen rieng tu\",\"contentMd\":\"# noi dung\","
                + "\"summaryOfChanges\":\"Them muc dich moi\",\"requiresReconsent\":true,"
                + "\"affectedPurposes\":[\"SERVICE_CORE\"],"
                + "\"effectiveFrom\":\"" + NEXT_MONTH + "\","
                + "\"reason\":\"Cap nhat chinh sach theo ND356\"}";
    }

    private static PolicyVersionAdminService.AdminPolicyRow draftRow() {
        return new PolicyVersionAdminService.AdminPolicyRow(
                version(null), PolicyVersionAdminService.PolicyVersionStatus.DRAFT, true);
    }

    private static PolicyVersionAdminService.AdminPolicyRow publishedRow() {
        return new PolicyVersionAdminService.AdminPolicyRow(
                version(UUID.fromString("00000000-0000-0000-0000-0000000000d1")),
                PolicyVersionAdminService.PolicyVersionStatus.SCHEDULED, true);
    }

    private static PolicyVersion version(UUID publishedBy) {
        return new PolicyVersion(DRAFT_ID, PolicyType.PRIVACY, "2.0", "vi",
                "Chinh sach quyen rieng tu", "# noi dung", null, "a".repeat(64),
                "Them muc dich moi", true, List.of("SERVICE_CORE"), NEXT_MONTH, null,
                publishedBy, NOW);
    }
}
