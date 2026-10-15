# CatCheck — Bug & Risk Audit (`opencode-bug-found.md`)

Read-only audit of the CatCheck backend (Spring Boot 4 / JDK 25 / PostgreSQL 18 / Flyway) and
frontend (React 19 / TypeScript / Vite / TanStack Query / Zustand).

**Method:** 12 parallel read-only sub-agents split by module (6 backend groups, 3 frontend groups,
plus re-runs for two groups whose first pass produced hallucinated paths). No source files were
modified. High-severity findings marked **[verified]** were independently re-checked against the
actual code by the lead; the rest were reported by a module agent against real files and line
references but not all were re-read line-by-line.

**Severity legend:** CRITICAL = breaks security/authentication or the core product; HIGH = breaks a
major workflow, leaks resources, or bypasses a security control; MEDIUM = incorrect behavior,
concurrency/consistency, or resource issues with narrower blast radius.

> Note: `CLAUDE.md` references `context/HANDOFF.md` and `context/spec/*`, which are absent from the
> working tree, so spec-conformance could not be checked; findings are based on code reasoning alone.

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 1 |
| HIGH     | 9 |
| MEDIUM   | ~30 |

Areas with the most serious issues: **identity/auth (MFA, session revocation, step-up)** and the
**core scan/color-chart pipeline**.

---

## CRITICAL

### C1. TOTP multi-factor authentication is not enforced before a full session is issued [verified]
- **Type:** Security
- **Location:** `backend/src/main/java/com/catcheck/identity/api/AuthController.java:171`
- **What:** When `mfaService.totpEnabled(userId)` is true, `login` **still** calls
  `sessionGateway.establish(...)` (lines 172 and 180), storing a fully authenticated `AuthPrincipal`
  in the HTTP session, then merely returns `{mfaRequired:true}`. The only place `mfaLevel` is checked
  is `AdminMfaGateFilter` (`identity/infrastructure/security/AdminMfaGateFilter.java:63`), and it
  applies only to the `/api/v1/admin` path. No filter enforces `MfaLevel` for normal user endpoints.
- **Why:** The second factor is a client-side hint. After a correct password the server issues a
  valid session with the user's real roles and `mfaLevel=NONE`; a caller can ignore `mfaRequired`
  and use the session on every non-admin endpoint.
- **Impact:** Complete bypass of TOTP MFA for every user who enabled it — password compromise alone
  yields full account access.
- **Fix:** Do not `establish` the authenticated session when TOTP is required. Create a limited
  pre-auth state and only establish / `markMfaTotpVerified` after `/totp/verify` succeeds; enforce
  `mfaLevel` on user endpoints that require it.

---

## HIGH

### H1. OAuth (Google) login bypasses TOTP and the lockout timestamp [verified]
- **Type:** Security
- **Location:** `backend/src/main/java/com/catcheck/identity/application/OAuthService.java:59`
- **What:** `findOrCreate` resolves/creates the account (existing Google `sub`, or existing-by-email
  link at lines ~100–120) and returns; it never consults `MfaTotpRepository`.
  `OAuth2LoginSuccessHandler.java:58-59` then calls `sessionGateway.establish(...)` directly.
  `requireLoginAllowed` (lines 162-166) checks only `status == ACTIVE` and `isProcessingRestricted`.
- **Why:** An account with TOTP active receives a full session with no second factor. The lockout
  path also only sets `lockedUntil` while leaving `status=ACTIVE`, so OAuth ignores lockouts too.
- **Impact:** MFA bypass and brute-force-lockout bypass for any Google-linked account.
- **Fix:** After resolving the account, force the TOTP challenge (or reject) when a TOTP record is
  active, and enforce `lockedUntil` in `requireLoginAllowed`.

### H2. Admin "lock" and "revoke all sessions" do not terminate live sessions [verified]
- **Type:** Security
- **Location:** `backend/src/main/java/com/catcheck/identity/application/AdminUserService.java:137`
  (and `:182`)
- **What:** `lock` and `revokeAllSessions` call `sessionRepository.revokeAllByUserId(...)` where
  `sessionRepository` is `DeviceSessionRepository` (field at line 49) — the custom
  `user_device_session` table. The authenticated principal actually lives in the Spring Session
  (`spring_session`) store. Other paths correctly use `AuthenticatedSessionRevoker`
  (`SpringSecuritySessionGateway.java:200-206`), which also calls `deleteHttpSessions`. No
  per-request filter validates `user_device_session`.
- **Why:** Marking device-session rows revoked is bookkeeping only; the live HTTP session cookie
  keeps working.
- **Impact:** Admin lock / forced logout do not take effect in real time; a stolen session survives
  the lock, contrary to spec.
- **Fix:** Inject and call `AuthenticatedSessionRevoker.revokeAllByUserId(userId)` (or additionally
  `deleteHttpSessions`) in `AdminUserService.lock` / `revokeAllSessions`.

### H3. Step-up re-authentication never expires for the life of the session [verified]
- **Type:** Security
- **Location:** `backend/src/main/java/com/catcheck/identity/infrastructure/security/SpringSecuritySessionGateway.java:146`
- **What:** `markReauthenticated(int windowSeconds)` ignores `windowSeconds` and stores only
  `principal.withReauthenticated(true)` (lines 154-155); `isReauthenticated()` (line 193) reads that
  boolean, which is never reset. `SessionService.reauthenticate` calls `markReauthenticated(300)`
  and `AuthController.reauth` returns a cosmetic `reauthExpiresAt` of now+300s.
- **Why:** The advertised 5-minute window is not implemented.
- **Impact:** One step-up permanently unlocks every operation guarded by `StepUpVerificationPort`
  (personal-data export, account erasure, processing restriction) for the whole session.
- **Fix:** Persist an absolute expiry (`Instant.now().plusSeconds(windowSeconds)`) and compare
  against the clock in `isReauthenticated()`.

### H4. Color-chart unique index breaks the publish/archive workflow and can 500 create-draft [verified]
- **Type:** Business logic / schema
- **Location:** `backend/src/main/resources/db/migration/V9__colorchart.sql:128-129`;
  `backend/src/main/java/com/catcheck/colorchart/application/ColorChartAdminService.java:163-198`
- **What:** `CREATE UNIQUE INDEX uq_color_chart_active_per_batch ON color_chart
  (product_line, coalesce(production_batch,''), status)` includes `status`, so it enforces "at most
  one row **per status**" rather than "at most one ACTIVE". `publish` archives the current ACTIVE
  chart then activates the new one; a second publish collides with the already-archived row and is
  caught and rethrown as `ConflictException(COLOR_CHART_IN_USE)` (409). `createDraft`
  (`:61-98`) performs no such catch, so a second DRAFT for the same line/batch raises
  `DataIntegrityViolationException` → HTTP 500.
- **Why:** The index key does not match its documented intent.
- **Impact:** Admin can effectively publish once per (line, batch); subsequent publishes fail with a
  misleading 409 and creating a second draft returns 500.
- **Fix:** Use a partial unique index: `ON color_chart (product_line, coalesce(production_batch,''))
  WHERE status = 'ACTIVE'`. Also map `DataIntegrityViolationException` to 409 in `createDraft`.

### H5. OpenCV native `Mat` memory leak on every scan [verified]
- **Type:** Technical / resource leak
- **Location:** `backend/src/main/java/com/catcheck/scan/application/ColorPipelineService.java:79-142`;
  `backend/src/main/java/com/catcheck/scan/domain/color/port/VisionEngine.java:79-84`
- **What:** `analyze` creates native frames via `rawImagePort.decode(...)`,
  `rawImagePort.cropToRoi(...)`, `visionEngine.detectCard(...)` (returns `canonicalCard`) and
  `rawImagePort.calibrate(...)` (returns an xyz frame). `VisionEngine.FrameHandle` / `XyzFrame` are
  plain interfaces that do **not** extend `AutoCloseable`, and `analyze` has no `try/finally`, so the
  concrete `OpenCv*` handles (which do implement `close()`) are never released except at GC.
- **Why:** Off-heap native buffers are not deterministically freed.
- **Impact:** Native memory grows unbounded under sustained scanning → eventual process OOM, invisible
  to JVM heap monitoring.
- **Fix:** Make `FrameHandle`/`XyzFrame` extend `AutoCloseable` and close all frames in `finally`.

### H6. pH-band admin edit can make every subsequent scan fail [verified]
- **Type:** Business logic / validation
- **Location:** `backend/src/main/java/com/catcheck/colorchart/application/PhBandAdminService.java:89-115`;
  `backend/src/main/java/com/catcheck/scan/domain/color/PhBandClassifier.java:54-57`
- **What:** `validateCoverage` returns early when the band is deactivated and only checks pairwise
  overlaps among active bands. It never verifies full coverage of the pH range nor the presence of
  the mandatory `IN_RANGE` band. `PhBandClassifier.classify` throws `IllegalStateException` when no
  band contains the pH (`orElseThrow`, lines 56-57), and `nearBoundary` requires `IN_RANGE`.
- **Why:** Admin can create a coverage gap or deactivate `IN_RANGE`, after which every scan pipeline
  call throws → 500. Once deactivated, the band no longer appears in the active-only list, so it may
  be hard to re-activate via the API.
- **Impact:** A single admin edit can take down the entire scan feature.
- **Fix:** Validate full coverage with the required `IN_RANGE` band present; forbid deactivation of
  required bands; resolve bands by code (not from the active-only list).

### H7. RRULE reminders are never scheduled or fired [verified]
- **Type:** Business logic
- **Location:** `backend/src/main/java/com/catcheck/reminder/domain/NextRunCalculator.java:14`
- **What:** `nextRun` returns `null` for any `scheduleKind != INTERVAL`. The create/patch paths accept
  `RRULE` and validate the rrule, but persist reminders with `next_run_at = NULL`, and the due-query
  selects `next_run_at IS NOT NULL`.
- **Why:** The RRULE scheduling mode has no next-run computation.
- **Impact:** An advertised scheduling mode silently never sends notifications.
- **Fix:** Implement RRULE expansion (or reject it with a validation error) and always persist a real
  `next_run_at`.

### H8. Double-refund race allows the same consumed credit entry to be refunded twice [verified]
- **Type:** Business logic / concurrency
- **Location:** `backend/src/main/java/com/catcheck/credit/application/FefoCreditConsumptionService.java:121`
- **What:** `refund` checks `creditLedgerPort.existsRefundReferencing(original.id())` (line 121)
  **before** acquiring the batch lock `lockBatchForRefund` (line 126). `credit_ledger.ref_id` is
  indexed but **not** unique (`V10__credit.sql:123-128`; only `idempotency_key` is unique).
  `CreditBatchSnapshot.canRefund` only asserts `remainingAmount + amount <= initialAmount`.
- **Why:** Two concurrent refunds with different idempotency keys both see no refund, then serialize
  on the batch lock; the second passes `canRefund` when other consumes freed capacity, restoring the
  credits again.
- **Impact:** Credit inflation / free scans from replaying one consumed entry.
- **Fix:** Add a partial unique constraint on refund `(ref_type, ref_id)`, and re-check existence
  **inside** the batch lock.

### H9. English locale is selectable but no English translations exist; UI stays Vietnamese [verified]
- **Type:** i18n / functional
- **Location:** `frontend/src/shared/config/constants.ts:4`,
  `frontend/src/shared/i18n/i18n.ts:15-16`,
  `frontend/src/shared/i18n/locales/en/` (empty), `frontend/public/locales/en/` (empty),
  `frontend/src/pages/settings/SettingsLanguagePage.tsx`
- **What:** `SUPPORTED_LOCALES = ["vi","en"]`, `supportedLngs:["vi","en"]`, `fallbackLng:"vi"`, and
  the language picker offers English; but the `en` directories contain no JSON namespace files, so
  `i18next-http-backend` 404s and falls back to Vietnamese.
- **Why:** Advertised option with no resources.
- **Impact:** Choosing English yields a fully Vietnamese UI; the feature is non-functional.
- **Fix:** Ship `en` namespace files, or remove `"en"` until translations exist.

---

## MEDIUM

### Backend — identity / privacy
- **M1. Step-up scope ignored.** `PrivacyStepUpVerificationAdapter.isVerified(userId, scope)` ignores
  `scope` and returns the session-wide `isReauthenticated()`
  (`identity/application/privacy/PrivacyStepUpVerificationAdapter.java:19`). One re-auth unlocks
  every privacy scope (`DATA_EXPORT`, `ACCOUNT_ERASE`, `DSAR_RESTRICT`, …).
  *Fix:* bind verification to the requested scope.
- **M2. DSAR records with no owner are readable by any authenticated user.**
  `DsarService.getByPublicRef` (`privacy/application/DsarService.java:169`) skips the ownership check
  when `request.userId() == null`; null-owner DSARs exist (see `DataExportJobService.enqueueOnBehalf`
  rejecting `userId == null`). Any authenticated user with a `publicRef` can read contact/identity
  data. *Fix:* require `Objects.equals(request.userId(), userId)`.
- **M3. Client-controlled OTP purpose on an unauthenticated endpoint.**
  `AuthController.requestOtp` derives `purpose` from the body (`AuthController.java:138`) with
  `userId = null`; any purpose (incl. login step-up / password reset) can be triggered for an
  arbitrary email → abuse as an OTP/email relay (mitigated by rate limits).
  *Fix:* whitelist purposes valid for anonymous callers.
- **M4. Email-OTP step-up always fails with an NPE.** `SessionService.verifyEmailOtp:239` calls
  `challenge.userId().equals(userId)`, but `requestOtp` always passes `userId = null`, so it NPEs →
  500. *Fix:* associate the challenge with the authenticated user, or use `Objects.equals`.
- **M5. Login lockout counter increments while already locked.**
  `LoginService` calls `recordFailure` before the lock check (`LoginService.java:86`), and
  `recordFailure` unconditionally extends `lockedUntil` and can push the account to permanent
  `LOCKED` (which the login path rejects regardless of `lockedUntil`). *Fix:* check lock/status first.

### Backend — scan / colorchart / insight / ai
- **M6. AI chat holds a DB transaction across the external LLM call.**
  `AiChatService.chat` is `@Transactional` and calls `provider.complete(prompt)` inside it
  (`ai/application/AiChatService.java:77,127`); `OpenAiCompatibleClient` builds its `RestClient`
  with no timeouts (`OpenAiCompatibleClient.java:20`). A hung provider holds a DB connection.
  *Fix:* perform retrieval + call outside the transaction; set connect/read timeouts.
- **M7. `createDraft` does not normalize blank `productionBatch` to null.**
  Contrast `updateDraft` (which does) at `ColorChartAdminService.java:61-98` vs `:125`. A chart
  stored with `''` is not found by the null-batch lookup but collides with the null-batch chart in
  the coalesced index. *Fix:* normalize in `createDraft`.
- **M8. Rule lookback has no deterministic tie-breaker.**
  `JdbcScanQueryRepository.findRecentForRules:143` orders by `captured_at DESC` only; trend logic
  assumes strict recency. *Fix:* add `, id DESC`.
- **M9. `evaluateRepeatedOutOfRange` can report one direction when both are present.**
  `insight/application/RuleEvaluationService.java:72-96` asserts all out-of-range scans lean one way
  without checking the opposite count. *Fix:* require opposite count below threshold / report mixed.
- **M10. Backfill apply runs the entire job in one transaction.**
  `scan/application/ChartBackfillService.java:198-201` wraps the whole recompute batch; long lock
  hold and all-or-nothing on one bad row. *Fix:* per-batch transactions.
- **M11. AI provider exceptions are swallowed without logging.**
  `OpenAiCompatibleClient.java:51-53` catches `RuntimeException` and returns empty, hiding outages.
  *Fix:* log/metric before returning empty.

### Backend — community / content / reminders / notification
- **M12. Moderation audit loses the affected author for hide/remove.**
  `CommunityModerationService.moderate:59` looks up the author **after** `moderateReport` changes
  the content status; `findReportTargetAuthor` filters `status='PUBLISHED'`
  (`JdbcCommunityRepository.java:181-190`), so `authorId` is null exactly for destructive actions.
  *Fix:* resolve the author before mutating.
- **M13. Moderators can moderate reports about their own content.**
  `CommunityModerationService.moderate` never compares the actor to the content author (unlike
  content's `CONTENT_SELF_APPROVAL_FORBIDDEN`). *Fix:* reject self-moderation.
- **M14. Report moderation TOCTOU (no row lock).** `findReport` is a plain `SELECT`
  (`JdbcCommunityRepository.java:210`); two moderators can both pass the OPEN check and both apply
  actions/audit. *Fix:* `FOR UPDATE` or a guarded conditional UPDATE.
- **M15. Reaction toggle returns an inverted/wrong state.**
  `JdbcCommunityRepository.toggleReaction:175` returns `update(...) >= 0` for insert (always true)
  and `== 1` for delete (true when a row was removed); the controller echoes it as `active`, so a
  successful un-like reports `active=true`. *Fix:* use `> 0` / return the resulting state.
- **M16. Broadcast dedupe key collapses repeat broadcasts within one minute + inflated `queued`.**
  `SystemBroadcastService.java:51` uses a minute-bucket dedupe prefix and increments `queued` (line
  67) even though `enqueue` may insert nothing (dedupe conflict). *Fix:* unique/idempotency key per
  broadcast; count actual inserts.
- **M17. Legacy push-token hand-off not revoked.**
  `JdbcPushSubscriptionRepository.revokeInstallationIdOwnedByOtherUser:98` revokes only by `fid`;
  a registration using `legacy_token` does not revoke the previous owner → cross-user push exposure.
  *Fix:* also revoke by `legacy_token`.
- **M18. Report rate-limit quota consumed before validation.**
  `CommunityService.java:136` calls `requireRateLimit` before target/self-report validation, so
  invalid attempts burn the quota. *Fix:* validate first, rate-limit last.
- **M19. Quiet-hours end boundary delays notifications by a day.**
  `QuietHours.isQuietAt` (`notification/domain/QuietHours.java:28`) treats the exact end time as
  quiet for wrapping windows, so `nextAllowedAfter` schedules the next day. *Fix:* make `end`
  exclusive.
- **M20. Offset computation can overflow `int`.**
  `CommunityService.java:50` (`page * size`) and `CommunityModerationService.java:38` compute the
  OFFSET as a 32-bit product. *Fix:* use `long`/clamp.
- **M21. Comment insert race returns 500.**
  `JdbcCommunityRepository.insertComment` uses `queryForObject` on an `INSERT ... SELECT ... WHERE
  EXISTS(published)`; if the post is hidden concurrently it throws `EmptyResultDataAccessException`.
  *Fix:* use `Optional` and map to `POST_NOT_FOUND`.

### Backend — shop / credit / place / media / export
- **M22. Export ignores user-selected sections.** `ExportRequestService` normalizes and persists
  `sections` (`ExportRequestService.java:84`), but `ExportRenderData` has no `sections` field and the
  PDF builder appends every section unconditionally. *Fix:* thread sections into rendering.
- **M23. Export data window computed in UTC, ignoring the requested timezone.**
  `ExportGenerationService.java:98-99` (and `ExportRequestService.java:77-78`) use
  `atStartOfDay(ZoneOffset.UTC)` while `zone` is only used for labels → range shifts by the UTC
  offset (e.g. 7h for Asia/Ho_Chi_Minh). *Fix:* compute bounds with the job's zone.
- **M24. Export pagination drops the next cursor at the max page size.**
  `ExportJobRepositoryAdapter.java:65` `fetchSize = Math.min(limit + 1, 100)`; with `limit = 100`,
  `hasMore = size > 100` is always false, so jobs beyond page 1 (100 rows) are unreachable.
  *Fix:* fetch `limit + 1` without the 100 cap for the sentinel.
- **M25. Over-large/invalid uploads leak temp `.part` files.**
  `LocalImageStorage.writeAtomically:232-241` deletes the temp file only on `IOException`, but
  `copyBounded` throws `BusinessRuleException` for oversized/unsupported payloads. *Fix:* clean up in
  `finally`.
- **M26. `place_booking` slot unique constraint permanently reserves slots incl. cancelled.**
  `V19__place.sql:61` `UNIQUE (place_id, booking_date, time_slot)` has no status filter, so a
  `CANCELLED` booking blocks the slot forever. *Fix:* partial unique index excluding non-blocking
  statuses.
- **M27. Signed media reads unprotected when the signing key is blank.**
  `LocalImageStorage.openSigned` computes a MAC with the blank key while `presignedUrl` refuses to
  operate when blank; the media GET is unauthenticated. *Fix:* fail closed when key blank.

### Frontend
- **M28. History KPI/summary ignores the selected range/day.** `CatHistoryPage.tsx:47` calls
  `useScanSummary(catId)` with no `from/to` even though the hook/API support it, so KPI cards, the
  spectrum `scanCount`, the `ALL` chip count (`:216`) and `totalRecords` (`:310`) are all-time while
  the list is range-filtered → visible mismatches. *Fix:* pass the range window.
- **M29. INCONCLUSIVE scans rendered as abnormal (red warning).**
  `pages/history/webHistory.tsx:301` (`severity==="NORMAL" ? check : AlertTriangle`) and
  `features/history/components.tsx:239` (`classification !== "IN_RANGE"`) treat INCONCLUSIVE as
  abnormal, contradicting `ABNORMAL_CLASSIFICATIONS` and `displayableScanCount`. *Fix:* handle
  NEUTRAL explicitly.
- **M30. `RequireEntitlement` guard is a hardcoded no-op.**
  `app/router/guards/RequireEntitlement.tsx:9` returns `true` unconditionally; `UpsellPage` is
  unreachable and any future gated route would be ungated. *Fix:* implement or remove.
- **M31. `getNextPageParam` can return `null`.** `features/history/hooks.ts:35` and
  `features/credit/hooks.ts:50` return `null` (a valid page param in TanStack Query) when
  `hasMore` is true but `nextCursor` is null → repeated first-page fetches. *Fix:* return
  `undefined` in that case.
- **M32. Hardcoded `vi-VN` locale in date/time formatting.**
  `pages/scan/webPanels.tsx:457`, `pages/trends/webTrends.tsx:58,497` (and shared formatters
  `shared/lib/format/formatDate.ts`, `formatRelative.ts`, `formatNumber.ts`) ignore the active
  language. *Fix:* derive the locale from i18n.
- **M33. Health-flag explanation renders the Vietnamese-only field.**
  `features/insight/components.tsx:47` prints `flag.explanationVi` verbatim instead of using
  `messageKey`/`messageParams`. *Fix:* resolve via i18n.
- **M34. Fully-configured `openapi-fetch` client is never used.**
  `shared/api/client.ts:8-13` (csrf/locale/idempotency/error middleware) has no consumers; each
  feature re-implements fetch with a different BASE_URL/error path. *Fix:* consolidate.
- **M35. Notification preferences optimistic update has no rollback.**
  `pages/settings/NotificationPreferencesForm.tsx:56-59` sets `draft` optimistically; on failure the
  cache is unchanged so `draft` stays at the unsaved value (controls show a persist that didn't
  happen). *Fix:* `onError` restore/invalidate.
- **M36. Profile rename not propagated to the session store.**
  `pages/settings/SettingsProfilePage.tsx:141-152` updates the `["auth","profile"]` cache but not
  `setSession`, so `SessionUser.displayName` stays stale in the header, dashboard, own community
  labels and checkout default name. *Fix:* sync the session on success.
- **M37. Export wizard default dates are UTC and frozen at module load.**
  `features/export/store.ts:21-22` uses `new Date().toISOString().slice(0,10)` at module scope and
  once more in `reset()`; in UTC+7 before 07:00 the default "today" is yesterday and stays stale
  across midnight. *Fix:* compute local dates on demand.
- **M38. Date pickers cap `max` at the UTC "today".**
  `features/cat/components.tsx:451`, `pages/cat/CatDetailPage.tsx:1189`,
  `pages/onboarding/OnboardingCatPage.tsx:466` — in UTC+7 mornings today's date is rejected.
  *Fix:* use the local date.
- **M39. `useGroup2Can`/`useGroup3Can` bypass `AdminAccessContext`.** `pages/admin/group2Access.ts:9`
  and `group3Ops.ts:14` read the session store directly and return a fresh closure each render
  instead of delegating to `useAdminCan()`. *Fix:* delegate and memoize.

---

## Appendix — claims discarded during verification

- The first community agent reported findings against **non-existent files**
  (`community/repository/PostRepository.java`, `service/ReportService.java`, `service/PostService.java`,
  `service/CommentService.java`, `service/ReactionService.java`). Those findings were discarded and
  the module was re-audited against the real files (`JdbcCommunityRepository.java`,
  `CommunityService.java`, `CommunityModerationService.java`); only the re-audited findings
  (M12–M21) are included.
- Migration-agent notes about `credit_ledger.user_id` / `audit_log.actor_id` having no FK and the
  partial erasure behavior of `anonymize_user_append_only` describe **intentional design** documented
  in-migration (pseudonym rewrite path); they are not reported as bugs.
- `V27__community_reaction_multi.sql` PK change and `NOT VALID` check constraints were reviewed and
  are consistent with the code; not reported.
