package com.catcheck.place.api;

import com.catcheck.place.api.dto.PlaceBookingRequest;
import com.catcheck.place.api.dto.PlaceResponse;
import com.catcheck.place.api.dto.PlaceReviewRequest;
import com.catcheck.place.api.dto.PlaceReviewResponse;
import com.catcheck.place.application.PlaceService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {
    private final PlaceService service;
    public PlaceController(PlaceService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "listPlaces")
    public List<PlaceResponse> list(@RequestParam(required = false) String query,
                                   @RequestParam(required = false) String kind,
                                   @RequestParam(required = false) String area,
                                   @RequestParam(required = false) Double latitude,
                                   @RequestParam(required = false) Double longitude,
                                   @RequestParam(defaultValue = "50") int limit) {
        return service.search(query, kind, area, latitude, longitude, limit).stream().map(PlaceResponse::from).toList();
    }

    /** Phòng khám thú y thật quanh vị trí người dùng (OpenStreetMap), xếp gần → xa. */
    @GetMapping("/nearby")
    @Operation(operationId = "listNearbyPlaces")
    public List<PlaceResponse> nearby(@RequestParam Double latitude,
                                      @RequestParam Double longitude,
                                      @RequestParam(defaultValue = "10") int radiusKm) {
        return service.nearbyExternal(latitude, longitude, radiusKm * 1000).stream().map(PlaceResponse::from).toList();
    }

    @GetMapping("/{placeId}")
    @Operation(operationId = "getPlace")
    public PlaceResponse detail(@PathVariable UUID placeId) { return PlaceResponse.from(service.detail(placeId)); }

    @GetMapping("/{placeId}/reviews")
    @Operation(operationId = "listPlaceReviews")
    public List<PlaceReviewResponse> reviews(@PathVariable UUID placeId,
                                             @RequestParam(defaultValue = "20") int limit) {
        return service.reviews(placeId, limit).stream().map(PlaceReviewResponse::from).toList();
    }

    @PostMapping("/{placeId}/reviews")
    @Operation(operationId = "createPlaceReview")
    public ResponseEntity<Void> review(@CurrentUser SecurityPrincipal user, @PathVariable UUID placeId, @Valid @RequestBody PlaceReviewRequest request) {
        service.review(user.userId(), placeId, request.rating(), request.body());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{placeId}/bookings")
    @Operation(operationId = "createPlaceBooking")
    public ResponseEntity<Void> book(@CurrentUser SecurityPrincipal user, @PathVariable UUID placeId, @Valid @RequestBody PlaceBookingRequest request) {
        service.book(user.userId(), placeId, request.serviceCode(), request.date(), request.timeSlot(), request.note());
        return ResponseEntity.accepted().build();
    }
}
