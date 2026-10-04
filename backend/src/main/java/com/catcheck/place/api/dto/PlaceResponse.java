package com.catcheck.place.api.dto;

import com.catcheck.place.domain.Place;

import java.util.List;

public record PlaceResponse(String id, String name, String kind, String address, String area,
                            double latitude, double longitude, String phone, List<String> specialties,
                            List<String> badges, double rating, long reviewCount) {
    public static PlaceResponse from(Place p) { return new PlaceResponse(p.id().toString(), p.name(), p.kind(), p.address(), p.area(), p.latitude(), p.longitude(), p.phone(), p.specialties(), p.badges(), p.rating(), p.reviewCount()); }
}
