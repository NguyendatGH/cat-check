package com.catcheck.place.application;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.place.domain.ExternalPlace;
import com.catcheck.place.domain.Place;
import com.catcheck.place.domain.port.NearbyPlaceSource;
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
    private final NearbyPlaceSource osm;
    public PlaceService(PlaceRepository repository, NearbyPlaceSource osm) { this.repository = repository; this.osm = osm; }

    public List<ExternalPlace> nearbyExternal(double latitude, double longitude, int radiusMeters) {
        if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return List.of();
        double cos = Math.cos(Math.toRadians(latitude));
        return osm.nearby(latitude, longitude, radiusMeters).stream()
                .sorted(java.util.Comparator.comparingDouble(p -> Math.pow(p.latitude() - latitude, 2) + Math.pow((p.longitude() - longitude) * cos, 2)))
                .toList();
    }

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
