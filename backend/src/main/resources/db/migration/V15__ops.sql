-- V15: Operations & Audit (nhom K cua p4 §4.3 + §K1, §K3, §K3b, §K4).
-- Nguon: spec/parts/p4-domain-model-erd.md §K1/§K3/§K3b/§K4 va §4.6.2 (append-only),
--        spec/parts/p4-domain-model-erd.md §4.9.2 (danh muc migration).
-- Phu thuoc: V1 (uuidv7()), V5 (app_user — FK actor/subject).
--
-- p4 §4.6.2: `audit_log` la bang APPEND-ONLY. `REVOKE UPDATE, DELETE` o cuoi file.
-- Bang `email_otp` KHONG nam trong danh sach 5 bang append-only cua p4 §4.6.2
-- (no can UPDATE attempt_count/consumed_at) — khong revoke tren bang do.

-- ============================================================================
-- K0. audit_log — nhat ky dung cho moi actor. Append-only.
-- KHONG FK den app_user voi ON DELETE CASCADE: p4 §4.6.3 yeu cau giu nhat ky sau
-- khi tai khoan bi xoa (ON DELETE SET NULL) va p11 §11.11.3 yeu cau audit ton tai
-- it nhat 24 thang ke ca sau khi user xoa tai khoan.
-- ============================================================================
CREATE TABLE audit_log (
    id              UUID        PRIMARY KEY DEFAULT uuidv7(),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type      VARCHAR(16) NOT NULL,
    -- KHONG FK: p4 §4.6.3 — user/keo channel la ket noi, khong rang buoc khoa.
    -- NULL voi actor_type <> 'USER' (SYSTEM/PUBLIC) va voi sau khi user bi xoa.
    actor_id        UUID,
    actor_role      VARCHAR(32),
    subject_type    VARCHAR(16) NOT NULL,
    -- FK SET NULL: quan he nguoi dung nhu cong dan phai con sau khi user bi xoa.
    subject_user_id UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    action          VARCHAR(64) NOT NULL,
    result          VARCHAR(16) NOT NULL,
    -- KHONG bao gio chua PII tho: mat khau, OTP, token, secret, khoa, cau hinh (p4 §4.6.4).
    -- Chi ghi id, loai, hash — khong ghi ten, email, so dien thoai.
    metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    before_data     JSONB,
    after_data      JSONB,
    request_id      VARCHAR(64),
    ip_address      INET,
    user_agent      TEXT,

    CONSTRAINT ck_audit_actor_type   CHECK (actor_type IN ('USER', 'ADMIN', 'DPO', 'SYSTEM', 'JOB')),
    CONSTRAINT ck_audit_subject_type CHECK (subject_type IN ('USER', 'CAT', 'SCAN', 'SUBSCRIPTION',
        'DOG', 'PACK', 'ARTICLE', 'CONSENT', 'POLICY', 'NOTIFICATION', 'SETTING', 'JOB', 'SYSTEM')),
    CONSTRAINT ck_audit_result       CHECK (result IN ('SUCCESS', 'DENIED', 'ERROR')),
    -- Truong danh cho p11 §11.11.1: DENIED thi action luu trong `metadata.action`.
    CONSTRAINT ck_audit_denied_has_action CHECK (result <> 'DENIED' OR
        (metadata ? 'action' AND jsonb_typeof(metadata -> 'action') = 'string'))
);

CREATE INDEX idx_audit_actor    ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_subject  ON audit_log (subject_user_id, occurred_at DESC);
CREATE INDEX idx_audit_action   ON audit_log (action, occurred_at DESC);
CREATE INDEX idx_audit_time     ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_request  ON audit_log (request_id) WHERE request_id IS NOT NULL;
-- Truy van Privacy Center: theo loai + thoi gian (p11 §11.11.3).
CREATE INDEX idx_audit_privacy ON audit_log (subject_type, occurred_at DESC);

COMMENT ON TABLE audit_log IS 'Nhat ky dung append-only. 24 thang lam VAT cho thong tin ca nhan (p11 §11.11.3); co che ky so duoc xac dinh o M3/M6.';
COMMENT ON COLUMN audit_log.actor_role IS 'Snapshot role tai thoi diem hanh dong — thay vi join app_user_role, de audit doc duoc sau khi role bi thu hoi.';

-- ============================================================================
-- K1. idempotency_record — theo doi request ghi duy nhat (dsar, data_export).
-- UNIQUE dung mot BTREE cu 4 cot vi PostgreSQL khong unique bat bien composite co
-- NULL theo tuyet doi (it nhat 2 NULL) — p4 §K1 ghi ro ly do.
-- ============================================================================
CREATE TABLE idempotency_record (
    id                UUID        PRIMARY KEY DEFAULT uuidv7(),
    idempotency_key   VARCHAR(64) NOT NULL,
    -- CHI luu SHA-256; luu ban ro se lo cau hinh giu du lieu trong DB (p4 §K1).
    user_id_hash      CHAR(64)    NOT NULL,
    ip_hash           CHAR(64),
    method            VARCHAR(8)  NOT NULL,
    path              VARCHAR(255) NOT NULL,
    request_hash      CHAR(64)    NOT NULL,
    state             VARCHAR(12) NOT NULL DEFAULT 'PROCESSING',
    response_snapshot JSONB,
    response_status   SMALLINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,

    CONSTRAINT ck_idempotency_state CHECK (state IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_idempotency_method CHECK (method IN ('POST', 'PUT', 'PATCH', 'DELETE')),
    CONSTRAINT ck_idempotency_completed CHECK (
        (state = 'COMPLETED') = (response_status IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_idempotency_scope
    ON idempotency_record (COALESCE(user_id_hash, ip_hash), method, path, idempotency_key);
CREATE INDEX idx_idempotency_created ON idempotency_record (created_at);

-- ============================================================================
-- K3. job_run — nhat ky job nen (12 gio lau nhat), dung cho hung gia khoa PII.
-- Ghi cac lan doi khoa o day TRUOC khi ghi vao `audit_log` (action = 'CRYPTO.KEY_ROTATED').
-- ============================================================================
CREATE TABLE job_run (
    id               UUID        PRIMARY KEY DEFAULT uuidv7(),
    job_name         VARCHAR(64) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    duration_ms      INT,
    row_count        INT,
    -- KHONG ghi PII vao day (p11 §11.10.3).
    error_summary    TEXT,

    CONSTRAINT ck_job_run_status CHECK (status IN (
        'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL', 'SKIPPED')),
    CONSTRAINT ck_job_run_finish CHECK ((finished_at IS NULL) = (duration_ms IS NULL)),
    CONSTRAINT ck_job_run_count  CHECK (row_count IS NULL OR row_count >= 0)
);

CREATE INDEX idx_job_run_name_started ON job_run (job_name, started_at DESC);
-- Tim lan chay bị treo: dang RUNNING va bat dau da lau.
CREATE INDEX idx_job_run_running     ON job_run (started_at) WHERE status = 'RUNNING';

-- ============================================================================
-- K3b. crypto_canary — kiem tra hoat dong ma hoa khi khoi dong. KHONG doc PII that.
-- UNIQUE (purpose, key_version) chong canh tranh ghi 2 canary cho mot key version.
-- ============================================================================
CREATE TABLE crypto_canary (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    -- dot phu thuoc HKDF; khoi dong that neu thieu mot trong so luu lieu that cua app.
    purpose      VARCHAR(24) NOT NULL,
    key_version  SMALLINT    NOT NULL,
    -- [1 byte key_version][12 byte IV][ciphertext][16 byte tag]
    cipher_blob  BYTEA       NOT NULL,
    -- BYTEA, khong phai CHAR(16): day la 16 byte nhi phan ngau nhien (khong phai chuoi ky
    -- tu), va CHECK length(...) = 16 ben duoi tinh theo SO BYTE. CHAR(16) tung khien
    -- JdbcCryptoCanaryRepository.save() loi "value too long for type character(16)" vi
    -- driver gui gia tri bytea, Postgres phai ep ve van ban (hex) truoc khi so voi do dai
    -- ky tu — phat hien that qua ApplicationContextSmokeTest, khong phai suy doan.
    plaintext_tag BYTEA      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at  TIMESTAMPTZ,

    CONSTRAINT uq_crypto_canary_version UNIQUE (purpose, key_version),
    CONSTRAINT ck_crypto_canary_purpose CHECK (purpose IN (
        'pii.phone', 'pii.push_token', 'mfa.totp', 'pii.location')),
    CONSTRAINT ck_crypto_canary_keyver  CHECK (key_version >= 1),
    -- Dung de phat hien canary bi xoa de bo qua fail-fast.
    CONSTRAINT ck_crypto_canary_tag     CHECK (length(plaintext_tag) = 16)
);

COMMENT ON TABLE crypto_canary IS 'Canary MA thuoc tinh: giu de xac nhan sau khoa bi xoay ma app van decrypt duoc du lieu cu. KHONG chua PII that.';

-- ============================================================================
-- K4. daily_metrics — so lieu tổng hop theo ngay, KHONG theo nguoi dung.
-- KHONG FK: metrics ton tai sau khi user bi xoa (p4 §K4).
-- ============================================================================
CREATE TABLE daily_metrics (
    metric_date  DATE        NOT NULL,
    metric_key   VARCHAR(64) NOT NULL,
    value        BIGINT      NOT NULL,
    breakdown    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    computed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (metric_date, metric_key)
);

COMMENT ON TABLE daily_metrics IS 'Chi so toan hop, KHONG phan biet duoc nguoi dung. Job cong bo toi da 7 ngay xoa.';

-- ============================================================================
-- p4 §4.6.2: cap quyen UPDATE/DELETE tren audit_log.
-- V2 moi tao `catcheck_app`; `catcheck_job` co the chua ton tai (W3 se them o migration
-- role/job rieng) nen REVOKE cho role do duoc bao ve bang kiem tra ton tai.
-- ============================================================================
REVOKE UPDATE, DELETE ON audit_log FROM catcheck_app;

-- p4 §4.6.1: `catcheck_app` chi duoc SELECT + INSERT tren `audit_log` — khong co
-- quyen UPDATE/DELETE (append-only) va khong DELETE bat ky bang nao khac.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT ON
            audit_log, job_run, daily_metrics, idempotency_record, crypto_canary
        TO catcheck_app;
    END IF;
END $$;

DO $$
DECLARE
    locked_role text;
BEGIN
    SELECT rolname INTO locked_role
    FROM pg_roles
    WHERE rolname = 'catcheck_job';

    IF locked_role IS NOT NULL THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON audit_log FROM %I', locked_role);
    END IF;
END $$;

COMMENT ON TABLE audit_log IS 'Nhat ky dung append-only (24 thang lam VAT cho thong tin ca nhan, p11 §11.11.3). Co che ky so duoc xac dinh o M3/M6.';
