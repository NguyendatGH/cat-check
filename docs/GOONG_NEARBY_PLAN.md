# Goong Nearby Vet Clinics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The care map shows vet clinics near the user from Goong (VN map provider) in addition to OpenStreetMap, so places that nobody seeded still show up.

**Architecture:** Goong becomes a second `NearbyPlaceSource` next to `OsmVeterinaryClient`. `PlaceService` queries every source in parallel, tolerates a failing source, drops duplicates and sorts by distance. The frontend learns a third source `GOONG` and treats every non-CatCheck place as "external" (directions + call only).

**Tech Stack:** Spring Boot 4.1 / JDK 25 (`RestClient`, virtual threads, Caffeine), React + TypeScript + Vitest, i18next.

**Spec:** this conversation's decision: "add Goong as a second source next to OSM; keep MapLibre". Goong REST docs: https://help.goong.io/kb/rest-api-v2/ (Autocomplete v2, Place Detail v2).

## Global Constraints

- Do not log PII: never log coordinates, the Goong API key, or request URLs (the key travels in the query string).
- Do not add Flyway migrations, new Maven/npm dependencies, or change pinned versions.
- Coordinates sent to any upstream are rounded to 2 decimals (~1 km), same as OSM today.
- Goong is optional: blank `catcheck.place.goong.api-key` means the source returns `List.of()` without any HTTP call.
- Goong is paid per request: every HTTP call (autocomplete and detail) counts against a daily budget.
- Commit messages: plain conventional commits, no `Co-Authored-By`/`Claude-Session` trailers.

## Review Focus

1. API key leaking into logs/exceptions: a network error message from `RestClient` contains the full URL including `api_key`. Task 1 test `failureMessageNeverContainsTheApiKey`.
2. Cost abuse: `/api/v1/places/nearby` is public (`permitAll`). Daily budget + cache must bound Goong calls. Task 1 tests `budgetExhaustedMakesNoHttpCall`, `secondLookupOfSameRoundedPointIsCached`.
3. One upstream down must not blank the map: Task 2 test `oneFailingSourceStillReturnsTheOthers`.
4. The same clinic from OSM and Goong shown twice: Task 2 dedupe tests.
5. Users outside Vietnam (Goong has VN data only) waste budget: Task 1 test `outsideVietnamMakesNoHttpCall`.

---

### Task 0: Foundation (done by the coordinator before dispatch)

- `ExternalPlace` gains a last field `String source` (`"OSM"` | `"GOONG"`).
- `PlaceResponse.from(ExternalPlace, String)` returns `p.source()`.
- `OsmVeterinaryClient` is `@Order(1)` and exposes `public static final String SOURCE = "OSM"`.

### Task 1: Goong adapter (backend)

**Files:**
- Create: `backend/src/main/java/com/catcheck/place/infrastructure/goong/GoongProperties.java`
- Create: `backend/src/main/java/com/catcheck/place/infrastructure/goong/GoongVeterinaryClient.java`
- Modify: `backend/src/main/resources/application.yml` (under `catcheck:`), `.env.example`, `.env.prod.example`
- Test: `backend/src/test/java/com/catcheck/place/infrastructure/goong/GoongVeterinaryClientTest.java`

**Interfaces:**
- Consumes: `NearbyPlaceSource` (`MIN_RADIUS_METERS = 500`, `MAX_RADIUS_METERS = 25_000`, `List<ExternalPlace> nearby(double latitude, double longitude, long radiusMeters)`), `ExternalPlace(..., String source)`, the app's `java.time.Clock` bean.
- Produces: `@Component @Order(2) public class GoongVeterinaryClient implements NearbyPlaceSource` with `ID_PREFIX = "goong:"`, `SOURCE = "GOONG"`, constructor `GoongVeterinaryClient(GoongProperties properties, Clock clock)`.

`GoongProperties` — `@ConfigurationProperties(prefix = "catcheck.place.goong") record GoongProperties(String apiKey, String baseUrl, String keyword, int resultLimit, int dailyRequestBudget, Duration connectTimeout, Duration readTimeout)`; compact constructor applies defaults when blank/zero: `baseUrl = "https://rsapi.goong.io"`, `keyword = "thú y"`, `resultLimit = 10`, `dailyRequestBudget = 900` (≈27k/month, under Goong's 30k free requests), `connectTimeout = 5s`, `readTimeout = 10s`. Register with `@EnableConfigurationProperties(GoongProperties.class)` on the client class (pattern: `AiConfiguration`). YAML keys: `api-key: ${CATCHECK_PLACE_GOONG_API_KEY:}`, `base-url: ${CATCHECK_PLACE_GOONG_BASE_URL:https://rsapi.goong.io}`, `daily-request-budget: ${CATCHECK_PLACE_GOONG_DAILY_REQUEST_BUDGET:900}`. Env files get a commented block like the PayOS one.

Behaviour of `nearby(lat, lon, radiusMeters)`:
1. Key blank, or rounded point outside Vietnam (`8.0 ≤ lat ≤ 23.5`, `102.0 ≤ lon ≤ 117.5`) → `List.of()`, no HTTP.
2. Round lat/lon to 2 decimals; clamp radius to `[MIN, MAX]`; cache key `"%.2f,%.2f,%d"` (Locale.ROOT). Caffeine cache, 6 h TTL, max 500 entries.
3. Autocomplete (costs 1 budget unit; no budget → `List.of()`): `GET {baseUrl}/v2/place/autocomplete?api_key=…&input={keyword}&location={lat},{lon}&radius={ceil(radiusMeters/1000)}&limit={resultLimit}&more_compound=true`. Response: `{"predictions":[{"place_id","description","structured_formatting":{"main_text","secondary_text"},"compound":{"commune","province"}}],"status":"OK"}`. Missing `predictions` → empty.
4. Detail per prediction (1 budget unit each, only as many as the remaining budget allows; detail results cached by `place_id` for 24 h, max 5000, cache hits cost nothing), run in parallel on virtual threads: `GET {baseUrl}/v2/place/detail?api_key=…&place_id=…`. Response: `{"result":{"place_id","name","formatted_address","geometry":{"location":{"lat","lng"}},"compound":{"commune","province"}},"status":"OK"}`. A failing detail call skips that place only.
5. Map to `new ExternalPlace("goong:" + place_id, name ?: main_text, "CLINIC", formatted_address ?: description, commune ?: province ?: "", lat, lng, null, List.of(), "GOONG")`. Drop places farther than the clamped radius from the rounded center.
6. Autocomplete HTTP/IO failure → throw `new IllegalStateException("Goong lookup failed: " + e.getClass().getSimpleName())` with **no cause** (the cause message holds the URL with the key). Log only the exception class name.

Budget: per calendar day of `LocalDate.now(clock)`; resets when the date changes; thread-safe.

- [ ] **Step 1: Write failing tests** with a loopback `com.sun.net.httpserver.HttpServer` on port 0 (pattern: `PayOsPaymentGatewayTest`), counting requests per path and returning canned JSON:
  - `blankApiKeyMakesNoHttpCall` → empty list, 0 requests.
  - `outsideVietnamMakesNoHttpCall` → `nearby(48.85, 2.35, 10_000)` empty, 0 requests.
  - `mapsAutocompleteAndDetailIntoExternalPlaces` → 2 predictions → 2 places with id `goong:<id>`, source `GOONG`, kind `CLINIC`, name/address/area/coords from detail; autocomplete query contains `input=th%C3%BA+y` (or `%20`), `location=15.57,108.47`, `radius=10`, `limit=10`, `more_compound=true`.
  - `dropsPlacesBeyondTheRadius` → a detail 30 km away is not returned for radius 10 km.
  - `secondLookupOfSameRoundedPointIsCached` → `nearby(15.571,…)` then `nearby(15.574,…)` → autocomplete called once.
  - `budgetExhaustedMakesNoHttpCall` → budget 1: first lookup does autocomplete only (0 details fetched, empty result); a lookup at another point makes 0 requests; after the clock moves to the next day requests resume.
  - `failedDetailSkipsOnlyThatPlace` → one detail returns 500 → the other place is still returned.
  - `failureMessageNeverContainsTheApiKey` → autocomplete returns 500 (and separately: server stopped) → thrown exception's message does not contain the key and `getCause()` is null.
- [ ] **Step 2:** `cd backend && ./mvnw -B -q -Dtest=GoongVeterinaryClientTest -Dsurefire.failIfNoSpecifiedTests=false test` → FAIL (class missing).
- [ ] **Step 3:** Implement `GoongProperties`, `GoongVeterinaryClient`, YAML and env docs.
- [ ] **Step 4:** Same command → all PASS (`target/surefire-reports/*GoongVeterinaryClientTest.txt` shows `Failures: 0, Errors: 0`).
- [ ] **Step 5:** Do not commit; the coordinator integrates.

### Task 2: Multi-source merge in PlaceService (backend)

**Files:**
- Modify: `backend/src/main/java/com/catcheck/place/application/PlaceService.java`
- Modify: `backend/src/test/java/com/catcheck/place/api/PlaceControllerTest.java:186-189` (`mvcWithUpstream` passes `List.of(source)`)
- Test: `backend/src/test/java/com/catcheck/place/application/PlaceServiceTest.java` (new)

**Interfaces:**
- Consumes: `NearbyPlaceSource`, `ExternalPlace(..., String source)`, `ExternalServiceException(ErrorCode, String serviceName, Throwable cause, Object... args)`, `PlaceErrorCode.PLACE_UPSTREAM_UNAVAILABLE`.
- Produces: `public PlaceService(PlaceRepository repository, List<NearbyPlaceSource> sources)`; `nearbyExternal(double, double, long)` keeps its signature and validation.

Behaviour: query all sources in parallel (virtual-thread executor). If every source throws → `ExternalServiceException(PLACE_UPSTREAM_UNAVAILABLE, "nearby-places", firstFailure)`. Otherwise concatenate successes in source order (Spring injects the list ordered by `@Order`), drop a place when an earlier kept place is within 50 m, or within 300 m with the same normalized name (lowercase, strip diacritics via `Normalizer` NFD + remove `\p{M}`, `đ`→`d`, collapse non-alphanumerics; null names never match), then sort by distance as today.

- [ ] **Step 1: Write failing tests** in `PlaceServiceTest` (plain JUnit, lambda sources):
  - `mergesSourcesSortedByDistance`
  - `dropsAPlaceWithin50mOfAnEarlierOne` (keeps the first source's entry)
  - `dropsSameNameWithin300mIgnoringCaseAndDiacritics` ("Phòng khám thú y An Bình" vs "PHONG KHAM THU Y AN BINH" 200 m apart → 1 kept; different names 100 m apart → both kept)
  - `oneFailingSourceStillReturnsTheOthers`
  - `allSourcesFailingThrowsExternalServiceException`
  - `outOfRangeQueryIsRejectedBeforeAnySourceIsCalled`
- [ ] **Step 2:** `cd backend && ./mvnw -B -q -Dtest='PlaceServiceTest,PlaceControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test` → FAIL.
- [ ] **Step 3:** Implement in `PlaceService`; update `mvcWithUpstream`.
- [ ] **Step 4:** Same command → PASS.
- [ ] **Step 5:** Do not commit; the coordinator integrates.

### Task 3: Frontend shows Goong places (frontend)

**Files:**
- Modify: `frontend/src/features/place/api.ts:21` (`source?: "CATCHECK" | "OSM" | "GOONG"`)
- Modify: `frontend/src/pages/map/placeView.ts:62,85` (`PlaceSource = "catcheck" | "osm" | "goong"`)
- Modify: `frontend/src/pages/map/CatCareMapPage.tsx`, `frontend/src/pages/map/placeParts.tsx`
- Modify: `frontend/src/shared/i18n/locales/{vi,en}/map.json` (only `src/` locales; `public/locales` is generated)
- Test: existing tests next to these files, plus a mapping test for `GOONG`.

**Interfaces:**
- Consumes: `/api/v1/places/nearby` items with `source: "OSM" | "GOONG"`.
- Produces: nothing for other tasks.

Behaviour: every `place.source === "osm"` check that means "external place" becomes `place.source !== "catcheck"`. The source chip shows the provider name `t(\`source.${place.source}\`)` (`source.goong` = "Goong"). Rename `source.osmNote` → `source.externalNote` with a `{{provider}}` placeholder. Location messages stop naming OpenStreetMap: vi `nearbyLoading` "Đang tìm phòng khám thú y quanh bạn…", `nearbyFound` "Có thêm {{count}} phòng khám thú y quanh bạn.", `nearbyNone` "Không tìm thấy thêm phòng khám thú y nào trong bán kính {{radius}} km.", `nearbyError` "Chưa tra được phòng khám quanh bạn, đang hiển thị danh sách CatCheck."; en equivalents. `attribution` names both OpenStreetMap and Goong.

- [ ] **Step 1:** Write/adjust tests: `placeView` maps `"GOONG"` → `"goong"`; a Goong place renders the "source.goong" chip and the external note and no booking/detail link (same assertions the OSM path has).
- [ ] **Step 2:** `cd frontend && npx vitest run src/pages/map src/features/place` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `npx tsc -b && npx eslint --max-warnings=0 src/pages/map src/features/place && npx vitest run src/pages/map src/features/place` → all pass.
- [ ] **Step 5:** Do not commit; the coordinator integrates.

### Task 4: Integrate and verify (coordinator)

- [ ] Apply Task 1 and Task 2 worktree diffs; review every diff against this plan.
- [ ] `cd backend && ./mvnw -B test`; `cd frontend && npx tsc -b && npx eslint . --max-warnings=0 && npx vitest run`.
- [ ] Run the app (free ports if 8080/5173 are taken) with `CATCHECK_PLACE_GOONG_BASE_URL` pointing at a local stub that serves canned Goong JSON; `curl /api/v1/places/nearby?latitude=15.57&longitude=108.47` shows `OSM` and `GOONG` items; the map page shows Goong pins and chips.
- [ ] With a real key (if provided): same curl near Tam Kỳ / Đà Nẵng returns real Goong clinics.
- [ ] Commit.
