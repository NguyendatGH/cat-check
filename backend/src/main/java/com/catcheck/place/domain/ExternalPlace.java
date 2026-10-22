package com.catcheck.place.domain;

import java.util.List;

/** {@code source} là nhà cung cấp dữ liệu ("OSM", "GOONG"), trả nguyên cho client trong PlaceResponse. */
public record ExternalPlace(String id, String name, String kind, String address, String area,
                            double latitude, double longitude, String phone, List<String> badges, String source) { }
