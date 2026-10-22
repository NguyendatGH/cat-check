package com.catcheck.place.application;

import com.catcheck.place.api.PlaceErrorCode;
import com.catcheck.place.domain.ExternalPlace;
import com.catcheck.place.domain.Place;
import com.catcheck.place.domain.port.NearbyPlaceSource;
import com.catcheck.place.domain.PlaceReview;
import com.catcheck.place.domain.port.PlaceRepository;
import com.catcheck.shared.error.ExternalServiceException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class PlaceService {
    // Overpass có lúc mất >30 s; Goong ~1–2 s. Không bắt người dùng đợi nguồn chậm nhất.
    private static final Duration FAST_ANSWER = Duration.ofMillis(2500);
    // Không đóng: tác vụ của nguồn chậm phải chạy xong sau khi request đã trả lời.
    private static final ExecutorService BACKGROUND = Executors.newVirtualThreadPerTaskExecutor();

    private final PlaceRepository repository;
    private final List<NearbyPlaceSource> sources;
    private final Duration fastAnswer;

    @Autowired
    public PlaceService(PlaceRepository repository, List<NearbyPlaceSource> sources) { this(repository, sources, FAST_ANSWER); }

    PlaceService(PlaceRepository repository, List<NearbyPlaceSource> sources, Duration fastAnswer) {
        this.repository = repository;
        this.sources = sources;
        this.fastAnswer = fastAnswer;
    }

    public List<ExternalPlace> nearbyExternal(double latitude, double longitude, long radiusMeters) {
        if (!(Math.abs(latitude) <= 90) || !(Math.abs(longitude) <= 180)
                || radiusMeters <= 0 || radiusMeters > NearbyPlaceSource.MAX_RADIUS_METERS) {
            throw new IllegalArgumentException("nearby query out of range");
        }
        var futures = sources.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> s.nearby(latitude, longitude, radiusMeters), BACKGROUND))
                .toList();
        awaitFastAnswer(futures);
        List<ExternalPlace> found = new ArrayList<>();
        Throwable firstFailure = null;
        boolean anySucceeded = false;
        for (var future : futures) { // nguồn chưa xong bị bỏ qua lượt này, vẫn chạy tiếp để tự ghi cache
            if (!future.isDone()) continue;
            if (future.isCompletedExceptionally()) {
                if (firstFailure == null) firstFailure = future.exceptionNow();
            } else {
                found.addAll(future.resultNow());
                anySucceeded = true;
            }
        }
        if (firstFailure != null && !anySucceeded) {
            throw new ExternalServiceException(PlaceErrorCode.PLACE_UPSTREAM_UNAVAILABLE, "nearby-places", firstFailure);
        }
        List<ExternalPlace> kept = new ArrayList<>();
        for (ExternalPlace p : found) { // found theo thứ tự nguồn: nguồn trước thắng khi trùng
            if (kept.stream().noneMatch(k -> sameClinic(k, p))) kept.add(p);
        }
        double cos = Math.cos(Math.toRadians(latitude));
        return kept.stream()
                .sorted(Comparator.comparingDouble(p -> Math.pow(p.latitude() - latitude, 2) + Math.pow((p.longitude() - longitude) * cos, 2)))
                .toList();
    }

    /** Đợi tối đa {@code fastAnswer}; nếu lúc đó chưa nguồn nào thành công thì đợi tiếp tới nguồn thành công đầu tiên. */
    private void awaitFastAnswer(List<CompletableFuture<List<ExternalPlace>>> futures) {
        var all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            all.get(fastAnswer.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException ignored) {
            // kết quả từng nguồn được đọc riêng ở nơi gọi
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(PlaceErrorCode.PLACE_UPSTREAM_UNAVAILABLE, "nearby-places", e);
        }
        while (!all.isDone() && futures.stream().noneMatch(f -> f.state() == Future.State.SUCCESS)) {
            var pending = futures.stream().filter(f -> !f.isDone()).toArray(CompletableFuture[]::new);
            CompletableFuture.anyOf(pending).exceptionally(e -> null).join();
        }
    }

    private static boolean sameClinic(ExternalPlace a, ExternalPlace b) {
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude()) * Math.cos(Math.toRadians(a.latitude()));
        double meters = 6_371_000 * Math.hypot(dLat, dLon);
        String name = normalize(a.name());
        return meters <= 50 || (meters <= 300 && name != null && name.equals(normalize(b.name())));
    }

    /** Tên không dấu, chữ thường; null nếu rỗng để hai tên trống không bao giờ khớp nhau. */
    private static String normalize(String name) {
        if (name == null) return null;
        String n = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return n.isEmpty() ? null : n;
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
