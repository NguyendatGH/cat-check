package com.catcheck.place.domain.port;

import com.catcheck.place.domain.ExternalPlace;

import java.util.List;

/** Nguồn cơ sở bên ngoài (hiện là OpenStreetMap) quanh một toạ độ — hiện thực nằm ở infrastructure. */
public interface NearbyPlaceSource {
    List<ExternalPlace> nearby(double latitude, double longitude, int radiusMeters);
}
