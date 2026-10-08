package com.catcheck.place.infrastructure.osm;

import com.catcheck.place.domain.ExternalPlace;
import com.catcheck.place.domain.port.NearbyPlaceSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tra cứu phòng khám thú y thật quanh một toạ độ từ OpenStreetMap (Overpass API, `amenity=veterinary`).
 * Toạ độ người dùng được làm tròn 0,01° (~1 km) trước khi gọi ra ngoài và dùng làm khoá cache; không ghi log toạ độ.
 * Lỗi mạng ⇒ trả danh sách rỗng (UI vẫn có dữ liệu CatCheck).
 */
@Component
public class OsmVeterinaryClient implements NearbyPlaceSource {
    public static final String ID_PREFIX = "osm:";
    private static final String UNNAMED = "Phòng khám thú y (chưa rõ tên)";
    private static final String ENDPOINT = "https://overpass-api.de/api/interpreter";
    private static final Duration TTL = Duration.ofHours(1);
    private static final int MAX_CACHE = 500;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OsmVeterinaryClient.class);

    private record Cached(Instant at, List<ExternalPlace> places) { }

    private final RestClient client = RestClient.builder()
            .requestFactory(timeouts())
            .defaultHeader("User-Agent", "CatCheck/1.0")
            .defaultHeader("Accept", "application/json")
            .build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final java.time.Clock clock;

    public OsmVeterinaryClient(java.time.Clock clock) { this.clock = clock; }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory timeouts() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(25));
        return f;
    }

    @Override
    public List<ExternalPlace> nearby(double latitude, double longitude, int radiusMeters) {
        double lat = Math.round(latitude * 100.0) / 100.0;
        double lon = Math.round(longitude * 100.0) / 100.0;
        int radius = Math.clamp(radiusMeters, 500, 25_000);
        String key = String.format(Locale.ROOT, "%.2f,%.2f,%d", lat, lon, radius);
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(clock.instant())) return hit.places();
        List<ExternalPlace> places;
        try {
            places = fetchWithRetry(lat, lon, radius);
        } catch (RuntimeException e) {
            LOG.warn("Overpass lookup failed: {}", e.getClass().getSimpleName());
            if (hit != null) return hit.places();
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "OSM_LOOKUP_FAILED");
        }
        if (cache.size() >= MAX_CACHE) cache.clear();
        cache.put(key, new Cached(clock.instant(), places));
        return places;
    }

    private List<ExternalPlace> fetchWithRetry(double lat, double lon, int radius) {
        try {
            return fetch(lat, lon, radius);
        } catch (RuntimeException first) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return fetch(lat, lon, radius);
        }
    }

    private List<ExternalPlace> fetch(double lat, double lon, int radius) {
        // Chỉ lọc theo thẻ key=value (có chỉ mục): lọc theo tên bằng regex làm Overpass quá tải/timeout.
        String around = String.format(Locale.ROOT, "(around:%d,%.4f,%.4f)", radius, lat, lon);
        String ql = "[out:json][timeout:20];("
                + "nwr[\"amenity\"=\"veterinary\"]" + around + ";"
                + "nwr[\"healthcare\"=\"veterinary\"]" + around + ";"
                + "nwr[\"healthcare:speciality\"=\"veterinary\"]" + around + ";"
                + ");out center tags 80;";
        var form = new LinkedMultiValueMap<String, String>();
        form.add("data", ql);
        Map<String, Object> body = client.post().uri(ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        List<ExternalPlace> out = new ArrayList<>();
        if (body == null || !(body.get("elements") instanceof List<?> elements)) return out;
        for (Object o : elements) {
            if (!(o instanceof Map<?, ?> el)) continue;
            Map<?, ?> tags = el.get("tags") instanceof Map<?, ?> t ? t : Map.of();
            Double plat = number(el.get("lat"), el.get("center"), "lat");
            Double plon = number(el.get("lon"), el.get("center"), "lon");
            String name = text(tags.get("name"));
            if (plat == null || plon == null) continue;
            if (name == null) name = first(text(tags.get("operator")), text(tags.get("brand")), UNNAMED);
            String phone = first(text(tags.get("phone")), text(tags.get("contact:phone")));
            String street = String.join(" ", nonNull(text(tags.get("addr:housenumber")), text(tags.get("addr:street"))));
            String area = first(text(tags.get("addr:suburb")), text(tags.get("addr:district")), text(tags.get("addr:city")));
            String address = String.join(", ", nonNull(street.isBlank() ? null : street, area));
            boolean allDay = "24/7".equals(text(tags.get("opening_hours")));
            out.add(new ExternalPlace(ID_PREFIX + el.get("type") + "/" + el.get("id"), name, "CLINIC",
                    address, area == null ? "" : area, plat, plon, phone,
                    allDay ? List.of("24/7") : List.of()));
        }
        return out;
    }

    private static Double number(Object direct, Object center, String field) {
        if (direct instanceof Number n) return n.doubleValue();
        if (center instanceof Map<?, ?> c && c.get(field) instanceof Number n) return n.doubleValue();
        return null;
    }

    private static String text(Object v) {
        return v instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    private static String first(String... values) {
        for (String v : values) if (v != null) return v;
        return null;
    }

    private static List<String> nonNull(String... values) {
        List<String> r = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) r.add(v);
        return r;
    }
}
