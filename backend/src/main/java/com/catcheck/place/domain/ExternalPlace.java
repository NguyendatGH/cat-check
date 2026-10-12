package com.catcheck.place.domain;

import java.util.List;

public record ExternalPlace(String id, String name, String kind, String address, String area,
                            double latitude, double longitude, String phone, List<String> badges) { }
