package com.catcheck.place.domain;

import java.util.List;

/** Cơ sở lấy từ nguồn ngoài (OpenStreetMap): chỉ để xem/chỉ đường, không đặt lịch hay đánh giá. */
public record ExternalPlace(String id, String name, String kind, String address, String area,
                            double latitude, double longitude, String phone, List<String> badges) { }
