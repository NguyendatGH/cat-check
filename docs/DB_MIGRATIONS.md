# Database migrations summary

CatCheck uses Flyway on PostgreSQL 18 with the pgvector image. Snapshot as of 2026-10-18: 35 versioned
migrations (`V1`–`V35`, no `V17`) and 7 repeatable seeds.

| Folder | Holds |
|---|---|
| `backend/src/main/resources/db/migration/` | `V<n>__*.sql`: schema changes. Each runs once, in version order. |
| `backend/src/main/resources/db/seed/` | `R__seed_*.sql`: reference data. Each runs again whenever its file changes. |

## How it runs

- Flyway runs **when the backend starts**, in every profile (`spring.flyway.locations: classpath:db/migration,classpath:db/seed`).
  There is no separate migrate step. Deploying a new backend image applies the new migrations.
- Versioned files run first, in order. Repeatable seeds run after them whenever their checksum changes.
- Hibernate only validates the schema (`ddl-auto: validate`). Flyway is the only thing that changes it.
- The `local` profile enables `out-of-order: true`, because V13 was written after V14/V15 had already run on
  dev databases. New databases (CI, staging, prod) run in natural order and don't need it.
- The DB user must be allowed to `CREATE EXTENSION`. The Docker image's `POSTGRES_USER` is a superuser.
  The extensions are `citext`, `pgcrypto`, `btree_gist`, `unaccent`, `pg_trgm` and `vector`.
- **Never edit a migration that has already run.** Flyway stores a checksum per file and refuses to start
  when a file changes. Add a new version instead.

## Versioned migrations

### Framework and foundations

| Version | What it does |
|---|---|
| V1 | Extensions `citext`, `pgcrypto`, `btree_gist`. Functions `uuidv7()` (time-ordered IDs) and `set_updated_at()` (trigger that sets `updated_at`). |
| V2 | DB role `catcheck_app` (no password is set here) with `USAGE` on schema `public`. |
| V3 | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`: login sessions (Spring Session JDBC, copied verbatim from upstream). |
| V4 | `event_publication`: Spring Modulith event log (legacy v1 layout, so `use-legacy-structure: true`). |
| V4_1 | `shedlock`: job locks so a scheduled job runs on one instance at a time. Flyway reads it as version **4.1**, so it slots between V4 and V5. |

### Business schema (one module per file)

| Version | Module | Tables |
|---|---|---|
| V5 | identity | `app_user`, `user_identity` (Google), `user_role`, `email_otp`, `password_reset`, `user_device_session`, `user_mfa_totp`, `user_mfa_recovery_code`, `user_mfa_reset_request` |
| V6 | privacy | `consent_purpose`, `consent_record`, `policy_version`, `policy_acknowledgment`, `dsar_request`, `data_inventory_item`, `retention_policy`, `holiday_calendar`, `security_incident`, `user_activity_log`. View `consent_current`. |
| V7 | catalog | `cat_breed`, `package_plan`, `app_setting`, `care_tip`. **Seeds** cat breeds and care tips. |
| V8 | cat | `cat`, `cat_health_survey`, `cat_note`, `cat_clinical_sign_report` |
| V9 | colorchart | `reference_card_layout`, `color_chart`, `color_chart_point`, `ph_classification_band`, `litter_substrate_profile`, `chart_calibration_job`. **Seeds** the CC-CARD layout, the placeholder chart `CHART-PLACEHOLDER-v1` (7 pH points, 5.5–8.0), the 6 global pH bands and the default litter substrate. |
| V10 | credit | `activation_code`, `credit_batch`, `credit_ledger`, `user_entitlement` |
| V11 | scan | `scan`, `scan_image`, `scan_analysis`, `scan_analysis_recompute`, `scan_reassignment`. Adds `scan_id` to `cat_note` and `cat_clinical_sign_report`. `scan.current_analysis_id` is a deferred FK. |
| V12 | insight | `monitoring_rule` (**seeded** rules), `health_flag` |
| V13 | notification / reminder | `notification`, `notification_outbox`, `email_outbox`, `push_subscription`, `user_notification_preference`, `reminder` |
| V14 | export | `export_job` (PDF health report) |
| V15 | ops / audit | `audit_log`, `idempotency_record`, `job_run`, `crypto_canary`, `daily_metrics` |
| V16 | (all) | Database-level rules: `updated_at` triggers, "scan images exist only when the scan stores images" (I7), "pH bands never overlap" (I9, `EXCLUDE USING gist`), a `care_tip` check (I31), and `anonymize_user_append_only()` for user erasure (`SECURITY DEFINER`). |
| V17 | — | **Not present.** The number is reserved for row-level security (`V17__rls.sql`) and was never written. |

### Later features

| Version | What it does |
|---|---|
| V18 | Community: `community_post`, `community_comment`, `community_reaction`, `community_report` |
| V19 | Places: `place`, `place_review`, `place_booking`. **Seeds** places (later replaced by V28). |
| V20 | Shop: `shop_product` (**seeded**), `shop_cart_line`, `shop_order`, `shop_order_line` |
| V21 | AI assistant: `ai_document`, `ai_chunk`, `ai_conversation`, `ai_message`, `ai_citation` |
| V22 | `ai_document.active`: archived care tips stay in citation history but leave search |
| V23 | `dsar_export_job`: personal-data ZIP exports |
| V24 | `dsar_export_job.download_token_hash`: one-time download token (only its SHA-256 is stored) |
| V25 | `dsar_export_job.email_download_token_hash`: separate token for the email link |
| V26 | `job_run`: adds trigger type, dry-run, item counters and instance id, plus status `SKIPPED_THRESHOLD`. Backfills existing rows. |
| V27 | Community: one user can both LIKE and BOOKMARK a post; one open report per user per post/comment; category `ALL` is no longer allowed on posts |
| V28 | Replaces the HCMC place directory with Da Nang. Existing bookings are re-pointed to the new places. |
| V29 | Community comments: one-level replies (`parent_comment_id`, `reply_to_user_id`) and an optional image |
| V30 | Fix: "one ACTIVE chart per product line + batch" is now a partial unique index on ACTIVE rows only (it used to block a second DRAFT) |
| V31 | Fix: a credit consumption can be refunded only once (`uq_credit_ledger_refund_ref`) |
| V32 | Fix: a cancelled booking frees its time slot (the unique index now skips `CANCELLED`) |
| V33 | `community_comment_reaction`: likes on comments |
| V34 | AI hybrid search: `unaccent` + `pg_trgm`, function `ai_fold()` (lower-case, strip accents), folded text + trigram indexes on `ai_chunk`, chunk hashes for incremental reindex, `ai_message.request_key` for idempotent chat |
| V35 | AI vector search: `pgvector`, `ai_chunk.embedding vector(384)` + `embedding_model`. No ANN index yet (exact search). |

## Repeatable seeds (`db/seed`)

These run in **every profile, prod included**. Every file uses `INSERT … ON CONFLICT DO UPDATE`, so editing
a seed file and deploying overwrites the matching rows.

| File | Rows | Notes |
|---|---|---|
| `R__seed_app_setting.sql` | 31 runtime settings, including 6 `feature.*` kill switches | `value` is only reset when no admin has edited that key (`updated_by IS NULL`), so admin changes survive. |
| `R__seed_consent_purpose.sql` | 12 consent purposes | Optional purposes are never preselected. |
| `R__seed_data_inventory_item.sql` | personal-data inventory D1–D21 | |
| `R__seed_holiday_calendar.sql` | Vietnamese public holidays for **2026 and 2027** | **Add 2028 before 2028.** |
| `R__seed_package_plan.sql` | 5 plans: MINI, DAILY, PLUS, MULTI, CARE_BOX | File note: "Owner approval is still required before go-live." Overwrites admin edits whenever the file changes. |
| `R__seed_policy_version.sql` | 4 legal documents | File note: "Draft … Legal review and replacement are required before go-live." |
| `R__seed_retention_policy.sql` | data retention rules | Retention periods are configuration. |

## Rules enforced in the database

- **Append-only tables** (`REVOKE UPDATE, DELETE` from `catcheck_app`): `audit_log`, `credit_ledger`,
  `consent_record`, `policy_acknowledgment`. `policy_version` and `security_incident` cannot be deleted.
  These grants only matter if the app connects as `catcheck_app`. The Docker setup connects as the owner
  role (`DB_USER`), which these revokes do not restrict.
- IDs are UUIDv7. Times are `TIMESTAMPTZ` in UTC. Enums are `VARCHAR` + `CHECK`, never PostgreSQL `ENUM`.
- Seed rows use fixed UUIDs (`00000000-…`) so `ON CONFLICT (id)` can match them.

## Before go-live

1. **Color chart:** the only chart is the V9 placeholder, and the `prod` profile refuses to start while it is
   active. `backend/tools/publish-standard-color-chart.sql` publishes the standard pH scale (`CHART-STANDARD` v1)
   and archives the placeholder. It is a one-time operations script, not a migration. See
   [DEPLOYMENT.md](DEPLOYMENT.md), section 3.
2. **Package plans and legal documents** are drafts according to their seed files. Get them approved and
   update the seed files before launch.
3. **Holiday calendar** ends in 2027.
4. **Back up before every backend update.** Migrations run automatically on start, and Flyway has no
   automatic rollback.

## Adding a migration

1. Use the next free version: `V36__<what_it_does>.sql`. Do not reuse `V17`.
2. Check that the table and column names match the spec (CLAUDE.md: "look up schema names in p4"), and only
   add migrations the spec lists.
3. Prefer additive changes (new columns or tables). Remove old columns in a later release, after no
   running code reads them.
4. Run `./mvnw test`. `MigrationNamingTests` and `SchemaInvariantTests` check names and invariants on a real
   PostgreSQL through Testcontainers.
