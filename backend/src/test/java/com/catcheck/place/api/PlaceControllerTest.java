package com.catcheck.place.api;

import com.catcheck.place.application.PlaceService;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlaceControllerTest {

    private final PlaceService service = Mockito.mock(PlaceService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = AdminApiMockMvc.build(new PlaceController(service));
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

    /**
     * {@code uq_place_booking_slot} so khớp chuỗi {@code time_slot} nguyên văn: "9:00" và "09:00"
     * là hai khoá khác nhau, nên cùng một khung giờ đặt được hai lần nếu API nhận chuỗi tự do.
     */
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

    /** {@code service_code VARCHAR(48)}: chuỗi dài hơn phải là 400, không được rơi xuống DB thành 500. */
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
}
