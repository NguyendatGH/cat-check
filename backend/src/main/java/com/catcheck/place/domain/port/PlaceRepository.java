package com.catcheck.place.domain.port;

import com.catcheck.place.domain.Place;
import com.catcheck.place.domain.PlaceReview;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository {
    List<Place> search(String query, String kind, String area, Double latitude, Double longitude, int limit);
    Optional<Place> find(UUID id);
    List<PlaceReview> reviews(UUID placeId, int limit);
    void createReview(UUID userId, UUID placeId, int rating, String body);
    void createBooking(UUID userId, UUID placeId, String serviceCode, LocalDate date, String timeSlot, String note);
}
