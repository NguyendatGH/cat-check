package com.catcheck.place.domain;

import java.util.List;
import java.util.UUID;

public record Place(UUID id, String name, String kind, String address, String area,
                    double latitude, double longitude, String phone, List<String> specialties,
                    List<String> badges, double rating, long reviewCount) { }
