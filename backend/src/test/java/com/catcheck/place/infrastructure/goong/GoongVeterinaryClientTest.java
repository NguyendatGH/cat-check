package com.catcheck.place.infrastructure.goong;

import com.catcheck.place.domain.ExternalPlace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoongVeterinaryClientTest {

    private static final String KEY = "test-goong-key-123";
    private static final double LAT = 15.57;
    private static final double LON = 108.47;
    private static final long TEN_KM = 10_000;

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-22T03:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    private final List<String> predictions = new CopyOnWriteArrayList<>();
    private final Map<String, String> details = new ConcurrentHashMap<>();
    private final List<Map<String, String>> autocompleteQueries = new CopyOnWriteArrayList<>();
    private final List<String> detailIds = new CopyOnWriteArrayList<>();
    private volatile int autocompleteStatus = 200;
    private final java.util.Set<String> rateLimitedOnce = ConcurrentHashMap.newKeySet();
    private volatile String autocompleteBody;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v2/place/autocomplete", exchange -> {
            autocompleteQueries.add(params(exchange));
            respond(exchange, autocompleteStatus, autocompleteBody != null ? autocompleteBody
                    : "{\"predictions\":[" + String.join(",", predictions) + "],\"status\":\"OK\"}");
        });
        server.createContext("/v2/place/detail", exchange -> {
            Map<String, String> query = params(exchange);
            detailIds.add(query.get("place_id"));
            if (rateLimitedOnce.remove(query.get("place_id"))) {
                respond(exchange, 429, "");
                return;
            }
            String body = KEY.equals(query.get("api_key")) ? details.get(query.get("place_id")) : null;
            respond(exchange, body == null ? 500 : 200, body == null ? "" : body);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void blankApiKeyMakesNoHttpCall() {
        place("a", "Thú y A", 15.58, 108.47);

        assertThat(client("", 900).nearby(LAT, LON, TEN_KM)).isEmpty();
        assertThat(autocompleteQueries).isEmpty();
        assertThat(detailIds).isEmpty();
    }

    @Test
    void outsideVietnamMakesNoHttpCall() {
        place("a", "Thú y A", 15.58, 108.47);

        assertThat(client(KEY, 900).nearby(48.85, 2.35, TEN_KM)).isEmpty();
        assertThat(autocompleteQueries).isEmpty();
        assertThat(detailIds).isEmpty();
    }

    @Test
    void mapsAutocompleteAndDetailIntoExternalPlaces() {
        place("a", "Thú y A", 15.58, 108.47);
        place("b", "Thú y B", 15.56, 108.49);

        List<ExternalPlace> places = client(KEY, 900).nearby(15.571, 108.474, TEN_KM);

        assertThat(places).containsExactly(
                new ExternalPlace("goong:a", "Thú y A", "CLINIC", "Địa chỉ a", "Phường a", 15.58, 108.47, null, List.of(), "GOONG"),
                new ExternalPlace("goong:b", "Thú y B", "CLINIC", "Địa chỉ b", "Phường b", 15.56, 108.49, null, List.of(), "GOONG"));
        assertThat(autocompleteQueries).singleElement().satisfies(query -> {
            assertThat(query).containsEntry("api_key", KEY).containsEntry("input", "thú y")
                    .containsEntry("location", "15.55,108.45").containsEntry("radius", "10")
                    .containsEntry("limit", "10").containsEntry("more_compound", "true");
        });
        assertThat(detailIds).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void dropsPlacesBeyondTheRadius() {
        place("near", "Gần", 15.58, 108.47);
        place("far", "Xa 30 km", 15.84, 108.47);

        assertThat(client(KEY, 900).nearby(LAT, LON, TEN_KM)).extracting(ExternalPlace::id).containsExactly("goong:near");
    }

    @Test
    void lookupsInTheSame5kmCellShareTheCache() {
        place("a", "Thú y A", 15.58, 108.47);
        GoongVeterinaryClient client = client(KEY, 900);

        List<ExternalPlace> first = client.nearby(15.56, 108.46, TEN_KM);
        List<ExternalPlace> second = client.nearby(15.57, 108.47, TEN_KM); // cách ~1,5 km, cùng ô 15.55,108.45

        assertThat(second).isEqualTo(first).hasSize(1);
        assertThat(autocompleteQueries).hasSize(1);
        assertThat(detailIds).hasSize(1);
    }

    @Test
    void detailIsCachedByPlaceIdAcrossLookups() {
        place("a", "Thú y A", 15.58, 108.47);
        GoongVeterinaryClient client = client(KEY, 900);

        client.nearby(LAT, LON, TEN_KM);
        assertThat(client.nearby(LAT, LON, 12_000)).hasSize(1);

        assertThat(autocompleteQueries).hasSize(2);
        assertThat(detailIds).hasSize(1);
    }

    @Test
    void budgetExhaustedMakesNoHttpCall() {
        place("a", "Thú y A", 15.58, 108.47);
        GoongVeterinaryClient client = client(KEY, 1);

        assertThat(client.nearby(LAT, LON, TEN_KM)).isEmpty();
        assertThat(autocompleteQueries).hasSize(1);
        assertThat(detailIds).isEmpty();

        assertThat(client.nearby(10.77, 106.70, TEN_KM)).isEmpty();
        assertThat(client.nearby(LAT, LON, TEN_KM)).isEmpty();
        assertThat(autocompleteQueries).hasSize(1);

        now.set(now.get().plus(Duration.ofDays(1)));
        client.nearby(LAT, LON, TEN_KM);
        assertThat(autocompleteQueries).hasSize(2);
    }

    @Test
    void budgetLimitsHowManyDetailsAreFetched() {
        place("a", "Thú y A", 15.58, 108.47);
        place("b", "Thú y B", 15.56, 108.49);

        assertThat(client(KEY, 2).nearby(LAT, LON, TEN_KM)).hasSize(1);
        assertThat(detailIds).hasSize(1);
    }

    @Test
    void errorBodyWithoutPredictionsIsNotCached() {
        place("a", "Thú y A", 15.58, 108.47);
        autocompleteBody = "{\"error_message\":\"quota exceeded\",\"status\":\"OVER_QUERY_LIMIT\"}";
        GoongVeterinaryClient client = client(KEY, 900);

        assertThat(client.nearby(LAT, LON, TEN_KM)).isEmpty();

        autocompleteBody = null;
        assertThat(client.nearby(LAT, LON, TEN_KM)).extracting(ExternalPlace::id).containsExactly("goong:a");
    }

    @Test
    void failedDetailSkipsOnlyThatPlace() {
        place("a", "Thú y A", 15.58, 108.47);
        place("b", "Thú y B", 15.56, 108.49);
        String detailOfB = details.remove("b");
        GoongVeterinaryClient client = client(KEY, 900);

        assertThat(client.nearby(LAT, LON, TEN_KM)).extracting(ExternalPlace::id).containsExactly("goong:a");

        details.put("b", detailOfB);
        assertThat(client.nearby(LAT, LON, TEN_KM)).extracting(ExternalPlace::id)
                .as("a lookup with a failed detail is not cached").containsExactly("goong:a", "goong:b");
    }

    @Test
    void rateLimitedDetailIsRetriedOnce() {
        place("a", "Thú y A", 15.58, 108.47);
        place("b", "Thú y B", 15.56, 108.49);
        rateLimitedOnce.add("b");

        assertThat(client(KEY, 900).nearby(LAT, LON, TEN_KM)).extracting(ExternalPlace::id).containsExactly("goong:a", "goong:b");
        assertThat(detailIds).filteredOn("b"::equals).hasSize(2);
    }

    @Test
    void failureMessageNeverContainsTheApiKey() {
        place("a", "Thú y A", 15.58, 108.47);
        GoongVeterinaryClient client = client(KEY, 900);

        autocompleteStatus = 500;
        assertSanitizedFailure(client, LAT, LON);

        server.stop(0);
        assertSanitizedFailure(client, 10.77, 106.70);
    }

    @Test
    void noBeanWithoutApiKeySoOsmFailuresStillSurface() {
        new ApplicationContextRunner()
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(GoongVeterinaryClient.class)
                .withPropertyValues("catcheck.place.goong.api-key=  ")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GoongVeterinaryClient.class));
    }

    @Test
    void bindsFromPropertiesWithDefaults() {
        new ApplicationContextRunner()
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(GoongVeterinaryClient.class)
                .withPropertyValues("catcheck.place.goong.api-key=" + KEY, "catcheck.place.goong.daily-request-budget=")
                .run(ctx -> {
                    GoongProperties properties = ctx.getBean(GoongProperties.class);
                    assertThat(properties.apiKey()).isEqualTo(KEY);
                    assertThat(properties.baseUrl()).isEqualTo("https://rsapi.goong.io");
                    assertThat(properties.keyword()).isEqualTo("thú y");
                    assertThat(properties.resultLimit()).isEqualTo(10);
                    assertThat(properties.dailyRequestBudget()).isEqualTo(900);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(ctx).hasSingleBean(GoongVeterinaryClient.class);
                });
    }

    private static void assertSanitizedFailure(GoongVeterinaryClient client, double lat, double lon) {
        assertThatThrownBy(() -> client.nearby(lat, lon, TEN_KM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Goong lookup failed: ")
                .hasMessageNotContaining(KEY)
                .hasNoCause();
    }

    private GoongVeterinaryClient client(String apiKey, int budget) {
        GoongProperties properties = new GoongProperties(apiKey, "http://127.0.0.1:" + server.getAddress().getPort(),
                null, 0, budget, null, null);
        return new GoongVeterinaryClient(properties, clock);
    }

    /** Registers a clinic in both stubs; the detail carries its own name/address/area so mapping is checkable. */
    private void place(String id, String name, double lat, double lng) {
        predictions.add(String.format(Locale.ROOT, "{\"place_id\":\"%s\",\"description\":\"%s, Quảng Nam\","
                + "\"structured_formatting\":{\"main_text\":\"%s\",\"secondary_text\":\"Quảng Nam\"},"
                + "\"compound\":{\"commune\":\"Sai\",\"province\":\"Quảng Nam\"}}", id, name, name));
        details.put(id, String.format(Locale.ROOT, "{\"result\":{\"place_id\":\"%s\",\"name\":\"%s\","
                + "\"formatted_address\":\"Địa chỉ %s\",\"geometry\":{\"location\":{\"lat\":%s,\"lng\":%s}},"
                + "\"compound\":{\"commune\":\"Phường %s\",\"province\":\"Quảng Nam\"}},\"status\":\"OK\"}",
                id, name, id, lat, lng, id));
    }

    private static Map<String, String> params(HttpExchange exchange) {
        Map<String, String> query = new HashMap<>();
        for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        return query;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
