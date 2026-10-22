package com.catcheck.place.application;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.place.domain.ExternalPlace;
import com.catcheck.place.domain.port.NearbyPlaceSource;
import com.catcheck.place.domain.port.PlaceRepository;
import com.catcheck.shared.error.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceServiceTest {

    private static final double LAT = 10.0;
    private static final double LON = 106.0;

    /** Địa điểm cách điểm truy vấn {@code metersNorth} mét về phía bắc. */
    private static ExternalPlace place(String id, String name, double metersNorth, String source) {
        return new ExternalPlace(id, name, "CLINIC", "", "", LAT + Math.toDegrees(metersNorth / 6_371_000), LON,
                null, List.of(), source);
    }

    private static NearbyPlaceSource returning(ExternalPlace... places) {
        return (lat, lon, radius) -> List.of(places);
    }

    private static NearbyPlaceSource failing(String message) {
        return (lat, lon, radius) -> {
            throw new IllegalStateException(message);
        };
    }

    private static List<ExternalPlace> nearby(NearbyPlaceSource... sources) {
        return new PlaceService(Mockito.mock(PlaceRepository.class), List.of(sources)).nearbyExternal(LAT, LON, 10_000);
    }

    @Test
    void mergesSourcesSortedByDistance() {
        List<ExternalPlace> merged = nearby(
                returning(place("osm:far", "Alpha", 2_000, "OSM")),
                returning(place("goong:near", "Beta", 500, "GOONG"), place("goong:mid", "Gamma", 1_000, "GOONG")));

        assertThat(merged).extracting(ExternalPlace::id).containsExactly("goong:near", "goong:mid", "osm:far");
    }

    @Test
    void dropsAPlaceWithin50mOfAnEarlierOne() {
        List<ExternalPlace> merged = nearby(
                returning(place("osm:1", "Alpha", 1_000, "OSM")),
                returning(place("goong:1", "Beta", 1_040, "GOONG"), place("goong:2", "Gamma", 1_060, "GOONG")));

        assertThat(merged).extracting(ExternalPlace::id).containsExactly("osm:1", "goong:2");
    }

    @Test
    void dropsSameNameWithin300mIgnoringCaseAndDiacritics() {
        List<ExternalPlace> sameName = nearby(
                returning(place("osm:1", "Phòng khám thú y An Bình", 1_000, "OSM")),
                returning(place("goong:1", "PHONG KHAM THU Y AN BINH", 1_200, "GOONG")));
        assertThat(sameName).extracting(ExternalPlace::id).containsExactly("osm:1");

        List<ExternalPlace> distinct = nearby(
                returning(place("osm:1", "Phòng khám An Bình", 1_000, "OSM"), place("osm:2", "Thú y Đức", 1_400, "OSM")),
                returning(place("goong:1", "Phòng khám Tâm An", 1_100, "GOONG"), place("goong:2", "Thú y Đức", 1_800, "GOONG"),
                        place("goong:3", null, 1_200, "GOONG"), place("goong:4", null, 1_300, "GOONG")));
        assertThat(distinct).extracting(ExternalPlace::id)
                .containsExactly("osm:1", "goong:1", "goong:3", "goong:4", "osm:2", "goong:2");
    }

    @Test
    void oneFailingSourceStillReturnsTheOthers() {
        List<ExternalPlace> merged = nearby(failing("overpass down"), returning(place("goong:1", "Alpha", 1_000, "GOONG")));

        assertThat(merged).extracting(ExternalPlace::id).containsExactly("goong:1");
    }

    private static NearbyPlaceSource after(long millis, ExternalPlace... places) {
        return (lat, lon, radius) -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return List.of(places);
        };
    }

    private static List<ExternalPlace> nearbyWithin(Duration fastAnswer, NearbyPlaceSource... sources) {
        return new PlaceService(Mockito.mock(PlaceRepository.class), List.of(sources), fastAnswer).nearbyExternal(LAT, LON, 10_000);
    }

    @Test
    void slowSourceIsLeftOutOnceAnotherAnswered() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch slowFinished = new CountDownLatch(1);
        NearbyPlaceSource slow = (lat, lon, radius) -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            slowFinished.countDown();
            return List.of(place("osm:1", "Alpha", 500, "OSM"));
        };
        long started = System.nanoTime();

        List<ExternalPlace> merged = nearbyWithin(Duration.ofMillis(100), slow, returning(place("goong:1", "Beta", 900, "GOONG")));

        assertThat(merged).extracting(ExternalPlace::id).containsExactly("goong:1");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
        release.countDown();
        // Nguồn chậm vẫn chạy tiếp (để tự ghi cache), không bị huỷ.
        assertThat(slowFinished.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void waitsPastTheDeadlineUntilSomeSourceSucceeds() {
        List<ExternalPlace> merged = nearbyWithin(Duration.ofMillis(50),
                failing("overpass down"), after(300, place("goong:1", "Alpha", 1_000, "GOONG")));

        assertThat(merged).extracting(ExternalPlace::id).containsExactly("goong:1");
    }

    @Test
    void allSourcesFailingThrowsExternalServiceException() {
        assertThatThrownBy(() -> nearby(failing("first"), failing("second")))
                .isInstanceOfSatisfying(ExternalServiceException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(PlaceErrorCode.PLACE_UPSTREAM_UNAVAILABLE);
                    assertThat(e.serviceName()).isEqualTo("nearby-places");
                    assertThat(e.getCause()).hasMessage("first");
                });
    }

    @Test
    void outOfRangeQueryIsRejectedBeforeAnySourceIsCalled() {
        AtomicInteger calls = new AtomicInteger();
        PlaceService service = new PlaceService(Mockito.mock(PlaceRepository.class), List.of((lat, lon, radius) -> {
            calls.incrementAndGet();
            return List.of();
        }));

        assertThatThrownBy(() -> service.nearbyExternal(91, LON, 1_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.nearbyExternal(LAT, -181, 1_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.nearbyExternal(Double.NaN, LON, 1_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.nearbyExternal(LAT, LON, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.nearbyExternal(LAT, LON, NearbyPlaceSource.MAX_RADIUS_METERS + 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).hasValue(0);
    }
}
