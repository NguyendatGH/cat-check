package com.catcheck.place.domain.port;

import com.catcheck.place.domain.ExternalPlace;

import java.util.List;

public interface NearbyPlaceSource {
    int MIN_RADIUS_METERS = 500;
    int MAX_RADIUS_METERS = 25_000;

    List<ExternalPlace> nearby(double latitude, double longitude, long radiusMeters);
}
