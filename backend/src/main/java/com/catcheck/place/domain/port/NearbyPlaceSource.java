package com.catcheck.place.domain.port;

import com.catcheck.place.domain.ExternalPlace;

import java.util.List;

public interface NearbyPlaceSource {
    int MIN_RADIUS_METERS = 500;
    int MAX_RADIUS_METERS = 25_000;

    /** Làm tròn về lưới 0,05° (~5,5 km): người ở gần nhau dùng chung cache, nguồn ngoài không nhận toạ độ chính xác. */
    static double snap(double degrees) {
        return Math.round(degrees * 20.0) / 20.0;
    }

    List<ExternalPlace> nearby(double latitude, double longitude, long radiusMeters);
}
