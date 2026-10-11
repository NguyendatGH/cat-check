package com.catcheck.place.infrastructure;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link PlaceErrorCode} vào {@code ErrorCodeRegistry} — mỗi hằng một {@code @Bean ErrorCode}
 * riêng (đúng khuôn {@code InsightErrorCodeConfiguration}). Đã grep: không mã nào trùng với module khác.
 */
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
