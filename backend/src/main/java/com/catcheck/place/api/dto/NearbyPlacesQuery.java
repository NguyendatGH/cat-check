package com.catcheck.place.api.dto;

import com.catcheck.place.domain.port.NearbyPlaceSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NearbyPlacesQuery(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Min(1) @Max(NearbyPlaceSource.MAX_RADIUS_METERS / 1000) Integer radiusKm) {

    public static final int DEFAULT_RADIUS_KM = 10;

    public NearbyPlacesQuery {
        if (radiusKm == null) radiusKm = DEFAULT_RADIUS_KM;
    }

    public long radiusMeters() { return radiusKm * 1000L; }
}
