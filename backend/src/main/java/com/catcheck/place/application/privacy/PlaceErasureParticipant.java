package com.catcheck.place.application.privacy;

import com.catcheck.place.domain.port.PlaceRepository;
import com.catcheck.privacy.spi.ErasureParticipant;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PlaceErasureParticipant implements ErasureParticipant {

    public static final String NAME = "place";

    private final PlaceRepository repository;

    public PlaceErasureParticipant(PlaceRepository repository) {
        this.repository = repository;
    }

    @Override
    public String participantName() {
        return NAME;
    }

    @Override
    public void erase(UUID subjectId) {
        repository.clearBookingNotes(subjectId);
    }
}
