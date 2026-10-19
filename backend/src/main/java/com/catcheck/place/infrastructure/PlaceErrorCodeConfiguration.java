package com.catcheck.place.infrastructure;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaceErrorCodeConfiguration {

    @Bean
    ErrorCode placeNotFound() {
        return PlaceErrorCode.PLACE_NOT_FOUND;
    }

    @Bean
    ErrorCode bookingSlotTaken() {
        return PlaceErrorCode.BOOKING_SLOT_TAKEN;
    }

    @Bean
    ErrorCode placeUpstreamUnavailable() {
        return PlaceErrorCode.PLACE_UPSTREAM_UNAVAILABLE;
    }
}
