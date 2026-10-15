package com.catcheck.colorchart.api;

import com.catcheck.colorchart.application.ColorChartQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicColorChartControllerTest {

    @Test
    void phBandsResponseVariesByAcceptLanguage() throws Exception {
        ColorChartQueryService queryService = Mockito.mock(ColorChartQueryService.class);
        Mockito.when(queryService.findGlobalBands()).thenReturn(List.of());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PublicColorChartController(queryService)).build();

        mockMvc.perform(get("/api/v1/reference/ph-bands").header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(HttpHeaders.VARY, org.hamcrest.Matchers.hasItem(HttpHeaders.ACCEPT_LANGUAGE)));
    }
}
