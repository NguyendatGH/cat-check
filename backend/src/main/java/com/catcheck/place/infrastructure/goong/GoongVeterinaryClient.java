package com.catcheck.place.infrastructure.goong;

import com.catcheck.place.domain.ExternalPlace;
import com.catcheck.place.domain.port.NearbyPlaceSource;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

// Không có key thì không tạo bean: nguồn tắt mà vẫn trả [] sẽ che lỗi OSM thành "không có phòng khám".
@Component
@Order(2)
@ConditionalOnExpression("!'${catcheck.place.goong.api-key:}'.isBlank()")
@EnableConfigurationProperties(GoongProperties.class)
public class GoongVeterinaryClient implements NearbyPlaceSource {
    public static final String ID_PREFIX = "goong:";
    public static final String SOURCE = "GOONG";

    private static final Logger LOG = LoggerFactory.getLogger(GoongVeterinaryClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() { };
    // Goong chỉ có dữ liệu Việt Nam: ngoài khung này không đốt ngân sách.
    private static final double MIN_LAT = 8.0, MAX_LAT = 23.5, MIN_LON = 102.0, MAX_LON = 117.5;

    private record Prediction(String id, String description, String mainText, String commune, String province) { }

    private final GoongProperties properties;
    private final Clock clock;
    private final RestClient client;
    private final Cache<String, List<ExternalPlace>> lookups = Caffeine.newBuilder()
            .maximumSize(500).expireAfterWrite(Duration.ofHours(24)).build();
    private final Cache<String, ExternalPlace> details = Caffeine.newBuilder()
            .maximumSize(5000).expireAfterWrite(Duration.ofHours(24)).build();
    // Goong trả 429 khi ~10 detail tới cùng lúc; 3 luồng thì không, 10 detail vẫn xong trong ~1,5 s.
    private final Semaphore detailSlots = new Semaphore(3);
    private LocalDate budgetDay;
    private int spent;

    public GoongVeterinaryClient(GoongProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // HttpClient của JDK dùng chung một kết nối HTTP/2 cho các detail song song, khỏi bắt tay TLS 10 lần.
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory)
                .defaultHeader("Accept", "application/json").build();
    }

    @Override
    public List<ExternalPlace> nearby(double latitude, double longitude, long radiusMeters) {
        double lat = NearbyPlaceSource.snap(latitude);
        double lon = NearbyPlaceSource.snap(longitude);
        if (properties.apiKey().isEmpty() || lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON) {
            return List.of();
        }
        int radius = Math.clamp(radiusMeters, MIN_RADIUS_METERS, MAX_RADIUS_METERS);
        String key = String.format(Locale.ROOT, "%.2f,%.2f,%d", lat, lon, radius);
        List<ExternalPlace> hit = lookups.getIfPresent(key);
        if (hit != null) return hit;
        if (reserve(1) == 0) return List.of();

        List<Prediction> predictions = autocomplete(lat, lon, radius);
        // Body không có predictions (vd. hết quota) là lỗi chứ không phải "không có phòng khám": không cache.
        if (predictions == null) return List.of();
        Map<String, ExternalPlace> found = new HashMap<>();
        List<Prediction> misses = new ArrayList<>();
        for (Prediction p : predictions) {
            ExternalPlace cached = details.getIfPresent(p.id());
            if (cached != null) found.put(p.id(), cached);
            else misses.add(p);
        }
        List<Prediction> toFetch = misses.subList(0, reserve(misses.size()));
        boolean complete = toFetch.size() == misses.size();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = toFetch.stream().map(p -> CompletableFuture.supplyAsync(() -> detail(p), executor)).toList();
            for (int i = 0; i < futures.size(); i++) {
                ExternalPlace place = futures.get(i).join();
                if (place == null) {
                    complete = false;
                    continue;
                }
                details.put(toFetch.get(i).id(), place);
                found.put(toFetch.get(i).id(), place);
            }
        }

        List<ExternalPlace> places = predictions.stream().map(p -> found.get(p.id())).filter(Objects::nonNull)
                .filter(p -> meters(lat, lon, p) <= radius).toList();
        // Kết quả thiếu (hết ngân sách / detail lỗi) không được nhớ 24 giờ, nếu không sẽ che cả những phòng khám còn lại.
        if (complete) lookups.put(key, places);
        return places;
    }

    private List<Prediction> autocomplete(double lat, double lon, int radius) {
        Map<String, Object> body;
        try {
            body = client.get().uri("/v2/place/autocomplete?api_key={key}&input={input}&location={location}"
                            + "&radius={radius}&limit={limit}&more_compound=true",
                    Map.of("key", properties.apiKey(), "input", properties.keyword(),
                            "location", String.format(Locale.ROOT, "%.2f,%.2f", lat, lon),
                            "radius", (radius + 999) / 1000, "limit", properties.resultLimit()))
                    .retrieve().body(JSON);
        } catch (RuntimeException e) {
            // Thông điệp của RestClient chứa nguyên URL kèm api_key: không giữ cause, không log message.
            LOG.warn("Goong lookup failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Goong lookup failed: " + e.getClass().getSimpleName());
        }
        if (body == null || !(body.get("predictions") instanceof List<?> list)) return null;
        List<Prediction> out = new ArrayList<>();
        for (Object o : list) {
            if (out.size() >= properties.resultLimit()) break;
            if (!(o instanceof Map<?, ?> p) || text(p.get("place_id")) == null) continue;
            Map<?, ?> compound = map(p.get("compound"));
            out.add(new Prediction(text(p.get("place_id")), text(p.get("description")),
                    text(map(p.get("structured_formatting")).get("main_text")),
                    text(compound.get("commune")), text(compound.get("province"))));
        }
        return out;
    }

    /** Trả null khi detail lỗi hoặc thiếu toạ độ: chỉ bỏ riêng phòng khám đó. */
    private ExternalPlace detail(Prediction p) {
        try {
            Map<String, Object> body;
            try {
                body = detailBody(p.id());
            } catch (HttpClientErrorException.TooManyRequests e) {
                // Nhiều người tra cùng lúc vẫn có thể chạm giới hạn tốc độ: đợi chút rồi thử lại đúng một lần.
                Thread.sleep(400);
                body = detailBody(p.id());
            }
            Map<?, ?> result = body == null ? Map.of() : map(body.get("result"));
            Map<?, ?> location = map(map(result.get("geometry")).get("location"));
            if (!(location.get("lat") instanceof Number lat) || !(location.get("lng") instanceof Number lng)) return null;
            Map<?, ?> compound = map(result.get("compound"));
            String area = first(text(compound.get("commune")), text(compound.get("province")), p.commune(), p.province());
            String address = first(text(result.get("formatted_address")), p.description());
            return new ExternalPlace(ID_PREFIX + p.id(), first(text(result.get("name")), p.mainText()), "CLINIC",
                    address == null ? "" : address, area == null ? "" : area,
                    lat.doubleValue(), lng.doubleValue(), null, List.of(), SOURCE);
        } catch (RuntimeException e) {
            LOG.warn("Goong detail failed: {}", e.getClass().getSimpleName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private Map<String, Object> detailBody(String placeId) throws InterruptedException {
        detailSlots.acquire();
        try {
            return client.get().uri("/v2/place/detail?api_key={key}&place_id={id}",
                    Map.of("key", properties.apiKey(), "id", placeId)).retrieve().body(JSON);
        } finally {
            detailSlots.release();
        }
    }

    /** Mỗi request Goong tính phí: cấp tối đa {@code wanted} đơn vị còn lại trong ngày (theo clock). */
    private synchronized int reserve(int wanted) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(budgetDay)) {
            budgetDay = today;
            spent = 0;
        }
        int granted = Math.min(wanted, properties.dailyRequestBudget() - spent);
        spent += granted;
        return granted;
    }

    /** Xấp xỉ phẳng, đủ chính xác trong bán kính tối đa 25 km. */
    private static double meters(double lat, double lon, ExternalPlace p) {
        double dy = (p.latitude() - lat) * 111_320;
        double dx = (p.longitude() - lon) * 111_320 * Math.cos(Math.toRadians(lat));
        return Math.hypot(dx, dy);
    }

    private static Map<?, ?> map(Object v) {
        return v instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String text(Object v) {
        return v instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private static String first(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }
}
