package com.catcheck.colorchart.api;

import com.catcheck.colorchart.application.ColorChartQueryService;
import com.catcheck.colorchart.domain.BandSeverity;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.shared.i18n.I18nProperties;
import com.catcheck.shared.i18n.LocaleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicColorChartControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        ColorChartQueryService queryService = Mockito.mock(ColorChartQueryService.class);
        Mockito.when(queryService.findGlobalBands()).thenReturn(List.of(new PhClassificationBand(
                UUID.randomUUID(), null, "NORMAL", null, null, true, true, "Binh thuong", "Normal", null, null,
                BandSeverity.NORMAL, "color-ph-normal", "check-circle", false, 1, true, now, now)));
        Locale vi = Locale.forLanguageTag("vi");
        // Cùng LocaleResolver với ứng dụng thật (vi + en, mặc định vi), không phải bộ mặc định của MockMvc.
        mockMvc = MockMvcBuilders.standaloneSetup(new PublicColorChartController(queryService))
                .setLocaleResolver(new LocaleConfig(new I18nProperties(vi, List.of(vi, Locale.ENGLISH)))
                        .localeResolver())
                .build();
    }

    @Test
    void phBandsResponseVariesByAcceptLanguage() throws Exception {
        mockMvc.perform(get("/api/v1/reference/ph-bands").header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.VARY, org.hamcrest.Matchers.hasItem(HttpHeaders.ACCEPT_LANGUAGE)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "en-US,en;q=0.9|Normal",
            "vi-VN,vi;q=0.9,en;q=0.8|Binh thuong",
            "fr|Binh thuong"})
    void phBandLabelFollowsTheBrowserAcceptLanguageAndFallsBackToVietnamese(String acceptLanguage, String label)
            throws Exception {
        mockMvc.perform(get("/api/v1/reference/ph-bands").header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value(label));
    }

    @Test
    void phBandLabelIsVietnameseWithoutAnAcceptLanguageHeader() throws Exception {
        mockMvc.perform(get("/api/v1/reference/ph-bands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Binh thuong"));
    }
}
