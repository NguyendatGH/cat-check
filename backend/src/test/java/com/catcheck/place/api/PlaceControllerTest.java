package com.catcheck.place.api;

import com.catcheck.place.application.PlaceService;
import com.catcheck.place.domain.ExternalPlace;
import com.catcheck.place.domain.port.NearbyPlaceSource;
import com.catcheck.place.domain.port.PlaceRepository;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaceControllerTest {

    private final PlaceService service = Mockito.mock(PlaceService.class);
    private final MessageResolver messageResolver = unnamedMessageResolver();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = AdminApiMockMvc.build(new PlaceController(service, messageResolver));
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("USER"));
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
        Mockito.reset(service);
    }

    @Test
    void bookingInThePastIsRejectedBeforeTheServiceCanCreateIt() throws Exception {
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(post("/api/v1/places/{placeId}/bookings", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceCode\":\"CHECKUP\",\"date\":\"" + yesterday
                                + "\",\"timeSlot\":\"09:00\"}"))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(service);
    }

    @Test
    void timeSlotMustBeCanonicalHourMinuteSoTheSlotUniqueKeyCannotBeBypassed() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        for (String slot : new String[] {"9:00", "09:00 ", "09:00:00", "24:00", "sáng mai"}) {
            mockMvc.perform(post("/api/v1/places/{placeId}/bookings", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"serviceCode\":\"CHECKUP\",\"date\":\"" + tomorrow
                                    + "\",\"timeSlot\":\"" + slot + "\"}"))
                    .andExpect(status().isBadRequest());
        }

        Mockito.verifyNoInteractions(service);
    }

    @Test
    void serviceCodeLongerThanTheColumnIsRejectedAsValidationError() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/v1/places/{placeId}/bookings", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceCode\":\"" + "X".repeat(49) + "\",\"date\":\"" + tomorrow
                                + "\",\"timeSlot\":\"09:00\"}"))
                .andExpect(status().isBadRequest());

        Mockito.verifyNoInteractions(service);
    }

    @Test
    void canonicalBookingReachesTheService() throws Exception {
        UUID placeId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        mockMvc.perform(post("/api/v1/places/{placeId}/bookings", placeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceCode\":\"" + "X".repeat(48) + "\",\"date\":\"" + tomorrow
                                + "\",\"timeSlot\":\"09:00\"}"))
                .andExpect(status().isAccepted());

        Mockito.verify(service).book(Mockito.any(), Mockito.eq(placeId), Mockito.eq("X".repeat(48)),
                Mockito.eq(tomorrow), Mockito.eq("09:00"), Mockito.isNull());
    }


    private ResultActions nearby(MockMvc target, String latitude, String longitude, String radiusKm) throws Exception {
        var request = get("/api/v1/places/nearby").param("latitude", latitude).param("longitude", longitude);
        if (radiusKm != null) request.param("radiusKm", radiusKm);
        return target.perform(request);
    }

    private long radiusMetersPassedToService() {
        var invocations = Mockito.mockingDetails(service).getInvocations();
        assertThat(invocations).hasSize(1);
        return ((Number) invocations.iterator().next().getArguments()[2]).longValue();
    }

    @Test
    void latitudeOutOfRangeIs400WithViolationAndNeverReachesTheService() throws Exception {
        for (String latitude : new String[] {"999", "90.0001", "-90.0001", "NaN", "Infinity"}) {
            nearby(mockMvc, latitude, "105.8", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("latitude"));
        }
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void longitudeOutOfRangeIs400WithViolationAndNeverReachesTheService() throws Exception {
        for (String longitude : new String[] {"999", "180.0001", "-180.0001", "NaN", "-Infinity"}) {
            nearby(mockMvc, "21.0", longitude, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.violations[0].field").value("longitude"));
        }
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void radiusNotPositiveOrAboveTheCapIs400BeforeAnyUpstreamCall() throws Exception {
        for (String radius : new String[] {"-1", "0", "26", "2147483647", "99999999999"}) {
            nearby(mockMvc, "21.0", "105.8", radius)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void nonNumericValuesAre400AndTheBodyDoesNotEchoThem() throws Exception {
        String body = nearby(mockMvc, "10.77,106.70", "105.8", "mười")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("10.77").doesNotContain("106.70").doesNotContain("mười");
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void missingCoordinatesAreValidationErrorsNotServerErrors() throws Exception {
        mockMvc.perform(get("/api/v1/places/nearby"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void validCoordinatesAndBoundaryValuesAre200AndUseDefaultRadiusOf10Km() throws Exception {
        nearby(mockMvc, "10.77", "106.7", null).andExpect(status().isOk());
        assertThat(radiusMetersPassedToService()).isEqualTo(10_000L);

        for (String[] edge : new String[][] {{"90", "180"}, {"-90", "-180"}, {"0", "0"}}) {
            nearby(mockMvc, edge[0], edge[1], "1").andExpect(status().isOk());
        }
    }

    @Test
    void maximumRadiusIsConvertedToMetersExactly() throws Exception {
        nearby(mockMvc, "10.77", "106.7", "25").andExpect(status().isOk());

        assertThat(radiusMetersPassedToService()).isEqualTo(25_000L);
    }

    private MockMvc mvcWithUpstream(NearbyPlaceSource source) {
        return AdminApiMockMvc.build(new PlaceController(
                new PlaceService(Mockito.mock(PlaceRepository.class), source), messageResolver));
    }

    private static MessageResolver unnamedMessageResolver() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("place.unnamed", Locale.forLanguageTag("vi"), "Phòng khám thú y (chưa rõ tên)");
        messages.addMessage("place.unnamed", Locale.ENGLISH, "Veterinary clinic (name unknown)");
        return new MessageResolver(messages);
    }

    @Test
    void osmPlaceWithoutANameGetsTheUnnamedLabelInTheRequestLanguage() throws Exception {
        MockMvc realService = mvcWithUpstream((lat, lon, radius) -> java.util.List.of(
                new ExternalPlace("osm:node/1", null, "CLINIC", "", "", 10.77, 106.7, null, java.util.List.of())));

        nearby(realService, "10.77", "106.7", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Veterinary clinic (name unknown)"));
        realService.perform(get("/api/v1/places/nearby").param("latitude", "10.77").param("longitude", "106.7")
                        .locale(Locale.forLanguageTag("vi")))
                .andExpect(jsonPath("$[0].name").value("Phòng khám thú y (chưa rõ tên)"));
    }

    @Test
    void upstreamBadGatewayFromTheOsmClientBecomesBusiness503NotA500() throws Exception {
        MockMvc realService = mvcWithUpstream((lat, lon, radius) -> {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OSM_LOOKUP_FAILED");
        });

        nearby(realService, "10.77", "106.7", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("PLACE_UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void anyOtherUpstreamFailureAlsoBecomes503() throws Exception {
        MockMvc realService = mvcWithUpstream((lat, lon, radius) -> {
            throw new IllegalStateException("boom");
        });

        nearby(realService, "10.77", "106.7", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("PLACE_UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void emptyUpstreamResultIsStill200() throws Exception {
        MockMvc realService = mvcWithUpstream((lat, lon, radius) -> java.util.List.of());

        nearby(realService, "10.77", "106.7", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
