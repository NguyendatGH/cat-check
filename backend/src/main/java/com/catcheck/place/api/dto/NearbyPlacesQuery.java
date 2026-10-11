package com.catcheck.place.api.dto;

import com.catcheck.place.domain.port.NearbyPlaceSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Tham số {@code GET /places/nearby}. Ngoài khoảng ⇒ 400 {@code VALIDATION_FAILED} kèm
 * {@code violations}, không im lặng trả danh sách rỗng hay đẩy bán kính vô lý sang Overpass.
 * Trần bán kính lấy từ {@link NearbyPlaceSource#MAX_RADIUS_METERS} (cũng là trần client OSM và
 * bán kính app web gửi); {@code radiusKm} vắng ⇒ {@value #DEFAULT_RADIUS_KM} km.
 */
public record NearbyPlacesQuery(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Min(1) @Max(NearbyPlaceSource.MAX_RADIUS_METERS / 1000) Integer radiusKm) {

    public static final int DEFAULT_RADIUS_KM = 10;

    public NearbyPlacesQuery {
        if (radiusKm == null) radiusKm = DEFAULT_RADIUS_KM;
    }

    /** Mét tính bằng {@code long} để không tràn int dù tham số này đã bị chặn trần ở trên. */
    public long radiusMeters() { return radiusKm * 1000L; }
}
