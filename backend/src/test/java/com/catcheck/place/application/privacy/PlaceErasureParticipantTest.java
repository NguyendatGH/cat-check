package com.catcheck.place.application.privacy;

import com.catcheck.place.domain.port.PlaceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trước đây không có {@code ErasureParticipant} nào cho {@code place}: xoá tài khoản xong,
 * {@code place_booking.note} (văn bản tự do gửi phòng khám, thường có tên/số điện thoại) vẫn còn
 * nguyên. {@code place_review.body} cố ý KHÔNG bị đụng — xem javadoc của participant.
 */
class PlaceErasureParticipantTest {

    private final PlaceRepository repository = Mockito.mock(PlaceRepository.class);
    private final PlaceErasureParticipant participant = new PlaceErasureParticipant(repository);

    @Test
    void erasureStripsTheFreeTextNoteOfEveryBookingOfTheSubject() {
        UUID subject = UUID.randomUUID();

        participant.erase(subject);

        Mockito.verify(repository).clearBookingNotes(subject);
        Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void participantIsNamedAfterTheModule() {
        assertThat(participant.participantName()).isEqualTo("place");
    }
}
