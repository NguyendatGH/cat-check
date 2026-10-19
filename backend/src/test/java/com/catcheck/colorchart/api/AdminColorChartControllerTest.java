package com.catcheck.colorchart.api;

import com.catcheck.colorchart.application.ChartBackfillAdminService;
import com.catcheck.colorchart.application.ColorChartAdminService;
import com.catcheck.colorchart.application.PhBandAdminService;
import com.catcheck.colorchart.domain.BandSeverity;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.scan.api.ChartBackfill;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminColorChartControllerTest {

    private static final UUID CHART_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final ChartBackfillAdminService backfillService = Mockito.mock(ChartBackfillAdminService.class);
    private final PhBandAdminService phBandService = Mockito.mock(PhBandAdminService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = AdminApiMockMvc.build(new AdminColorChartController(
                Mockito.mock(ColorChartAdminService.class),
                phBandService,
                backfillService,
                new ColorChartRoleGuard()));
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    @Test
    void previewWithoutAChartAdminRoleIsForbidden() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPPORT"));

        mockMvc.perform(post("/api/v1/admin/color-charts/{id}/backfill-preview", CHART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":\"P90D\",\"reason\":\"Hieu chuan bang mau lo 2026A\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_ROLE_REQUIRED"));

        Mockito.verifyNoInteractions(backfillService);
    }

    @Test
    void previewWithCatalogRoleReturnsAcceptedAndPointsLocationAtTheGetEndpoint() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));

        mockMvc.perform(post("/api/v1/admin/color-charts/{id}/backfill-preview", CHART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":\"P90D\",\"reason\":\"Hieu chuan bang mau lo 2026A\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        "/api/v1/admin/color-charts/" + CHART_ID + "/backfill-preview"))
                .andExpect(jsonPath("$.window").value("P90D"));
    }

    @Test
    void previewWithAReasonShorterThanTenCharactersIsRejectedBeforeAnyJobStarts() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));

        mockMvc.perform(post("/api/v1/admin/color-charts/{id}/backfill-preview", CHART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":\"P90D\",\"reason\":\"ngan\"}"))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(backfillService);
    }

    @Test
    void previewOnAnArchivedChartSurfacesChartInUseAsConflict() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.doThrow(new BusinessRuleException(
                        ColorChartErrorCode.COLOR_CHART_IN_USE, CHART_ID.toString(), "ARCHIVED"))
                .when(backfillService).startPreview(eq(CHART_ID), any(), any());

        mockMvc.perform(post("/api/v1/admin/color-charts/{id}/backfill-preview", CHART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":\"P90D\",\"reason\":\"Hieu chuan bang mau lo 2026A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COLOR_CHART_IN_USE"));
    }

    @Test
    void getPreviewReturnsFlippedCountAndLargestDeltaPh() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));
        UUID jobId = UUID.fromString("00000000-0000-0000-0000-00000000a0b1");
        Mockito.when(backfillService.impact(CHART_ID)).thenReturn(
                new ChartBackfill.BackfillImpact(40L, 3L, new BigDecimal("0.40"), jobId,
                        List.of(new ChartBackfill.BackfillSample(UUID.randomUUID(),
                                new BigDecimal("6.8"), "SLIGHTLY_HIGH", new BigDecimal("0.40"), true))));

        mockMvc.perform(get("/api/v1/admin/color-charts/{id}/backfill-preview", CHART_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluated").value(40))
                .andExpect(jsonPath("$.flipped").value(3))
                .andExpect(jsonPath("$.maxAbsDeltaPh").value(0.40))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.samples[0].flippedClassification").value(true));
    }

    @Test
    void applyWithoutAPreviewSurfacesIncompleteAsUnprocessableEntity() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_SUPER"));
        Mockito.doThrow(new BusinessRuleException(
                        ColorChartErrorCode.COLOR_CHART_INCOMPLETE, "backfillPreview"))
                .when(backfillService).startApply(eq(CHART_ID), any());

        mockMvc.perform(post("/api/v1/admin/color-charts/{id}/backfill-apply", CHART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Ap dung bang mau da duyet\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("COLOR_CHART_INCOMPLETE"));
    }

    @Test
    void applyWithoutAChartAdminRoleIsForbidden() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("DPO"));

        mockMvc.perform(post("/api/v1/admin/color-charts/{id}/backfill-apply", CHART_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Ap dung bang mau da duyet\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_ROLE_REQUIRED"));
    }

    @Test
    void bandLabelFollowsABrowserAcceptLanguageWithQualityValues() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        Mockito.when(phBandService.listBands()).thenReturn(List.of(new PhClassificationBand(
                UUID.randomUUID(), null, "NORMAL", null, null, true, true, "Binh thuong", "Normal", null, null,
                BandSeverity.NORMAL, "color-ph-normal", "check-circle", false, 1, true, now, now)));

        mockMvc.perform(get("/api/v1/admin/ph-classification-bands").header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Normal"));
    }

    @Test
    void inactiveBandIsResolvedByCodeForTheETagCheckAndAnUnknownCodeIsNotFound() throws Exception {
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        PhClassificationBand inactive = new PhClassificationBand(UUID.randomUUID(), null, "LEGACY", null, null,
                true, true, "Cu", "Old", null, null, BandSeverity.NORMAL, "color-ph-normal", "check-circle",
                false, 9, false, now, now);
        Mockito.when(phBandService.getBand("LEGACY")).thenReturn(inactive);
        Mockito.when(phBandService.updateBand(eq("LEGACY"), any())).thenReturn(inactive);
        Mockito.when(phBandService.getBand("MISSING"))
                .thenThrow(new NotFoundException(ColorChartErrorCode.PH_BAND_NOT_FOUND, "MISSING"));

        mockMvc.perform(patch("/api/v1/admin/ph-classification-bands/{code}", "LEGACY")
                        .header("If-Match", ETag.of(inactive))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LEGACY"));
        mockMvc.perform(patch("/api/v1/admin/ph-classification-bands/{code}", "MISSING")
                        .header("If-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PH_BAND_NOT_FOUND"));
    }
}
