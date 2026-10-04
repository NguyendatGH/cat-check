package com.catcheck.place.application;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.place.domain.Place;
import com.catcheck.place.domain.PlaceReview;
import com.catcheck.place.domain.port.PlaceRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PlaceService {
    private final PlaceRepository repository;
    public PlaceService(PlaceRepository repository) { this.repository = repository; }

    public List<Place> search(String query, String kind, String area, Double latitude, Double longitude, int limit) {
        return repository.search(query, kind, area, latitude, longitude, limit);
    }

    public Place detail(UUID placeId) {
        return repository.find(placeId).orElseThrow(() -> new NotFoundException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    public List<PlaceReview> reviews(UUID placeId, int limit) {
        detail(placeId);
        return repository.reviews(placeId, limit);
    }

    @Transactional
    public void review(UUID userId, UUID placeId, int rating, String body) {
        detail(placeId);
        repository.createReview(userId, placeId, rating, body);
    }

    @Transactional
    public void book(UUID userId, UUID placeId, String service, LocalDate date, String slot, String note) {
        detail(placeId);
        repository.createBooking(userId, placeId, service, date, slot, note);
    }
}
