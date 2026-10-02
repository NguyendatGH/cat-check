-- V5: Identity & Access (nhom A cua p4 §4.3 + §A1-A9).
-- Nguon: spec/parts/p4-domain-model-erd.md §A1..§A9 (chu so huu ten bang/cot/kieu),
--        spec/parts/p4-domain-model-erd.md §4.1 (UUIDv7, TIMESTAMPTZ, VARCHAR+CHECK),
--        spec/parts/p4-domain-model-erd.md §4.4.2 (danh muc enum nhom A).
-- Phu thuoc: V1 (citext + uuidv7()), V3 (SPRING_SESSION - khong tao FK sang bang framework).
--
-- LUU Y PHAT SINH (xem docs/handovers/A1.md):
--  1) `email_otp` bo sung 4 cot theo p11 §11.2.6 (`pepper_version`, `verified_at`,
--     `ticket_hash`, `ticket_expires_at`) va 3 gia tri `purpose` thieu (PASSWORD_RESET,
--     ACCOUNT_DELETE_CONFIRM, DATA_EXPORT_CONFIRM). p11 la chu miem xac thuc/OTP.
--  2) `password_reset` van duoc tao theo p4 §A5 (p4 la chu so huu schema nen danh muc
--     migration §4.9.2 phai giu dung 9 bang), nhung LUONG dat lai mat khau dien hinh
--     theo p11 §11.2.6 = OTP + ticket tren `email_otp`, khong phai magic link.
--  3) Trigger `set_updated_at()` cho cac bang co `updated_at` duoc tao o V16
--     (xem p4 §4.9.2), nen V5 CHI tao cot; tầng repository tu gan `updated_at` khi UPDATE.

-- ============================================================================
-- A1. app_user — tai khoan chu nuoi / admin. Goc so huu, KHONG co khoa ngoai.
-- ============================================================================
CREATE TABLE app_user (
    id                        UUID         PRIMARY KEY DEFAULT uuidv7(),
    email                     CITEXT       NOT NULL,
    email_verified_at         TIMESTAMPTZ,
    full_name                 TEXT         NOT NULL,
    -- AES-256-GCM o tang ung dung, khoa con `pii.phone`, AAD `app_user|phone|<id>`.
    -- Khong index duoc, khong LIKE duoc (p4 §A1 ghi chu nghiep vu).
    phone                     BYTEA,
    phone_key_version         SMALLINT,
    avatar_storage_key        TEXT,
    avatar_storage_provider   VARCHAR(16),
    locale                    VARCHAR(8)   NOT NULL DEFAULT 'vi',
    timezone                  VARCHAR(64)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status                    VARCHAR(24)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    onboarding_status         VARCHAR(32)  NOT NULL DEFAULT 'ACCOUNT_ONLY',
    failed_login_count        SMALLINT     NOT NULL DEFAULT 0,
    locked_until              TIMESTAMPTZ,
    password_changed_at       TIMESTAMPTZ,
    force_password_reset_at   TIMESTAMPTZ,
    processing_restricted_at  TIMESTAMPTZ,
    pseudonym_id              UUID,
    notification_prefs        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    referral_code_raw         VARCHAR(64),
    last_login_at             TIMESTAMPTZ,
    deletion_scheduled_at     TIMESTAMPTZ,
    anonymized_at             TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_full_name        CHECK (length(full_name) BETWEEN 1 AND 120),
    CONSTRAINT ck_app_user_avatar_provider  CHECK (avatar_storage_provider IN ('LOCAL', 'CLOUDINARY')),
    CONSTRAINT ck_app_user_locale           CHECK (locale IN ('vi', 'en')),
    CONSTRAINT ck_app_user_status           CHECK (status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'RESTRICTED', 'DELETION_REQUESTED', 'ANONYMIZED')),
    CONSTRAINT ck_app_user_onboarding       CHECK (onboarding_status IN (
        'ACCOUNT_ONLY', 'CAT_CREATED', 'SURVEY_DONE_OR_SKIPPED', 'COMPLETED')),
    CONSTRAINT ck_app_user_failed_logins    CHECK (failed_login_count >= 0),
    -- `phone_key_version IS NULL` <=> `phone IS NULL`; version >= 1 (p4 §A1).
    CONSTRAINT ck_app_user_phone_pair       CHECK (
        phone_key_version IS NULL OR (phone IS NOT NULL AND phone_key_version >= 1)),
    -- `processing_restricted_at` NOT NULL <=> status = 'RESTRICTED' (p4 §A1).
    CONSTRAINT ck_app_user_restricted_pair  CHECK (
        (status = 'RESTRICTED') = (processing_restricted_at IS NOT NULL)),
    CONSTRAINT ck_app_user_deletion_schedule CHECK (
        (status = 'DELETION_REQUESTED') = (deletion_scheduled_at IS NOT NULL)),
    CONSTRAINT ck_app_user_anonymized       CHECK (
        (status = 'ANONYMIZED') = (anonymized_at IS NOT NULL)),
    -- `force_password_reset_at` khac NULL va lon hon `password_changed_at` (p4 §A1).
    CONSTRAINT ck_app_user_force_reset      CHECK (
        force_password_reset_at IS NULL
        OR (password_changed_at IS NOT NULL AND force_password_reset_at > password_changed_at))
);

-- Truy van nong nhat cua luong auth + loc admin.
CREATE INDEX idx_app_user_status_not_active
    ON app_user (status) WHERE status <> 'ACTIVE';
CREATE INDEX idx_app_user_deletion_scheduled
    ON app_user (deletion_scheduled_at) WHERE deletion_scheduled_at IS NOT NULL;
CREATE INDEX idx_app_user_status_deletion
    ON app_user (status, deletion_scheduled_at);
CREATE UNIQUE INDEX uq_app_user_pseudonym
    ON app_user (pseudonym_id) WHERE pseudonym_id IS NOT NULL;

COMMENT ON TABLE app_user IS 'Tai khoan nguoi dung. KHONG co password_hash o day - hash nam o user_identity (p4 §A1, p11 §11.1.9).';
COMMENT ON COLUMN app_user.phone IS 'AES-256-GCM o tang dung dung, khoa con pii.phone, AAD app_user|phone|<id>. Khong index/LIKE duoc.';
COMMENT ON COLUMN app_user.email IS 'KHONG ma hoa (p11 §11.10.3): la khoa dang nhap, phai UNIQUE va tra o moi lan login.';

-- ============================================================================
-- A2. user_identity — phuong thuc dang nhap. FK -> app_user.
-- ============================================================================
CREATE TABLE user_identity (
    id                UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider          VARCHAR(16) NOT NULL,
    -- LOCAL: email chuan hoa. GOOGLE: claim `sub` (KHONG dung email - p4 §A2).
    provider_user_id  TEXT        NOT NULL,
    provider_email    CITEXT,
    email_verified    BOOLEAN     NOT NULL DEFAULT false,
    -- BCrypt strength 12 qua DelegatingPasswordEncoder, luu kem prefix `{bcrypt}`.
    -- CHI hop le voi provider = 'LOCAL' (p4 §A2, p11 §11.3.1).
    password_hash     TEXT,
    linked_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_identity_provider      CHECK (provider IN ('LOCAL', 'GOOGLE')),
    CONSTRAINT ck_identity_password      CHECK ((provider = 'LOCAL') OR (password_hash IS NULL))
);

CREATE UNIQUE INDEX uq_identity_provider_subject ON user_identity (provider, provider_user_id);
CREATE UNIQUE INDEX uq_identity_local_per_user ON user_identity (user_id) WHERE provider = 'LOCAL';
CREATE INDEX idx_identity_user ON user_identity (user_id);

COMMENT ON TABLE user_identity IS 'Moi app_user co >= 1 dong (bat bien I35, kiem o service). Doi LOCAL <-> GOOGLE khong duoc de trong.';

-- ============================================================================
-- A6. user_role — vai tro CON DON. PK composite (user_id, role).
-- KHONG phan cap vai tro (khong ROLE_HIERARCHY) — p11 §11.5.1.
-- ============================================================================
CREATE TABLE user_role (
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role        VARCHAR(32) NOT NULL,
    granted_by  UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_role_role CHECK (role IN (
        'USER', 'ADMIN_SUPPORT', 'ADMIN_CATALOG', 'ADMIN_SUPER', 'DPO', 'MODERATOR', 'VET'))
);

CREATE INDEX idx_user_role_non_user ON user_role (role) WHERE role <> 'USER';

-- ============================================================================
-- A3. email_otp — ma xac thuc 6 so. FK -> app_user (co the NULL: luong dang ky hai buoc).
-- KHONG bao gio luu ma tho, ke ca trong log (p11 §11.2.1).
-- ============================================================================
CREATE TABLE email_otp (
    id                UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID        REFERENCES app_user (id) ON DELETE CASCADE,
    email             CITEXT      NOT NULL,
    -- HMAC-SHA256(pepper, purpose || email || code), hex 64 ky tu (p11 §11.2.1).
    code_hash         TEXT        NOT NULL,
    -- Bat buoc: ma tho khong ton tai o dau de xoay pepper ma khong lam moi ma viet lai vo hinh
    -- (p11 §11.2.6 — cung bai hoc voi p11 §11.7.4 cho ma kich hoat).
    pepper_version    SMALLINT    NOT NULL DEFAULT 1,
    purpose           VARCHAR(24) NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    attempt_count     INT         NOT NULL DEFAULT 0,
    max_attempts      INT         NOT NULL DEFAULT 5,
    consumed_at       TIMESTAMPTZ,
    request_ip        INET,
    -- --- 4 cot bo sung theo p11 §11.2.6 (otp_ticket khong can bang rieng) ---
    verified_at       TIMESTAMPTZ,
    ticket_hash       CHAR(64),
    ticket_expires_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_email_otp_purpose      CHECK (purpose IN (
        'REGISTER_VERIFY', 'EMAIL_CHANGE', 'LOGIN_STEPUP', 'DSAR_VERIFY',
        'PASSWORD_RESET', 'ACCOUNT_DELETE_CONFIRM', 'DATA_EXPORT_CONFIRM')),
    CONSTRAINT ck_email_otp_code_hash     CHECK (length(code_hash) = 64),
    CONSTRAINT ck_email_otp_pepper        CHECK (pepper_version >= 1),
    CONSTRAINT ck_email_otp_attempts      CHECK (attempt_count >= 0 AND max_attempts > 0),
    CONSTRAINT ck_email_otp_expiry        CHECK (expires_at > created_at),
    CONSTRAINT ck_email_otp_verified_pair CHECK ((verified_at IS NULL) = (ticket_hash IS NULL)),
    CONSTRAINT uq_email_otp_ticket_hash   UNIQUE (ticket_hash)
);

-- Lay OTP moi nhat cho (email, purpose) + dem so lan gui trong cua so cooldown (M1 01c-2).
CREATE INDEX idx_email_otp_email_purpose ON email_otp (email, purpose, created_at DESC);
CREATE INDEX idx_email_otp_expiry_open   ON email_otp (expires_at) WHERE consumed_at IS NULL;
-- Mot challenge ACTIVE cho moi (email, purpose) — bat bien o DB khong the quen (p11 §11.2.6).
CREATE UNIQUE INDEX uq_email_otp_active_per_purpose
    ON email_otp (email, purpose) WHERE consumed_at IS NULL;

COMMENT ON COLUMN email_otp.code_hash IS 'HMAC-SHA256 co khoa (pepper) — KHONG phai SHA-256 trong ma. So sanh bang MessageDigest.isEqual (constant-time).';

-- ============================================================================
-- A5. password_reset — yeu cau dat lai mat khau.
-- Voi luong hien tai (p11 §11.2.6) dong nay ghi lai ticket PASSWORD_RESET da dung:
-- token_hash = SHA-256(otp_ticket), consumed_at = luc doi lay mat khau moi.
-- ============================================================================
CREATE TABLE password_reset (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    request_ip  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_password_reset_token UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_user ON password_reset (user_id, created_at DESC);

-- ============================================================================
-- A4(b). user_device_session — danh sach "thiet bi dang dang nhap".
-- KHONG chua token, KHONG tham gia xac thuc (Spring Session la noi xac thuc that).
-- Chi luu SHA-256 cua SPRING_SESSION.SESSION_ID de noi 1-1 va thu hoi dung phien.
-- ============================================================================
CREATE TABLE user_device_session (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    session_id_hash   CHAR(64)     NOT NULL,
    device_label      VARCHAR(100),
    user_agent        TEXT,
    ip_address        INET,
    remember_me       BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    revoked_at        TIMESTAMPTZ,
    revoke_reason     VARCHAR(32),

    CONSTRAINT uq_device_session_hash    UNIQUE (session_id_hash),
    CONSTRAINT ck_device_session_expiry  CHECK (expires_at > created_at),
    CONSTRAINT ck_device_session_reason  CHECK (revoke_reason IN (
        'USER_LOGOUT', 'USER_REVOKE_ONE', 'USER_REVOKE_ALL', 'PASSWORD_CHANGED',
        'ADMIN_LOCK', 'DELETION_REQUESTED', 'MAX_SESSIONS', 'EXPIRED')),
    CONSTRAINT ck_device_session_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

CREATE INDEX idx_device_session_user_active
    ON user_device_session (user_id, last_seen_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX idx_device_session_expiry_open
    ON user_device_session (expires_at) WHERE revoked_at IS NULL;

-- ============================================================================
-- A7. user_mfa_totp — bi mat TOTP da ma hoa AES-256-GCM (khoa con `mfa.totp`).
-- Bo cuc: [1 byte key_version][12 byte IV][ciphertext][16 byte tag] (p11 §11.10.3).
-- PK = user_id => "mot user mot dong" la bat bien cua DB chu khong phai cua code.
-- ============================================================================
CREATE TABLE user_mfa_totp (
    user_id            UUID        PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    secret_enc         BYTEA       NOT NULL,
    status             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    algorithm          VARCHAR(8)  NOT NULL DEFAULT 'SHA1',
    digits             SMALLINT    NOT NULL DEFAULT 6,
    period_seconds     SMALLINT    NOT NULL DEFAULT 30,
    -- Chong replay: tu choi moi ma co step <= last_used_step (p4 §A7).
    last_used_step     BIGINT,
    failed_count       SMALLINT    NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    pending_expires_at TIMESTAMPTZ,
    activated_at       TIMESTAMPTZ,
    key_version        SMALLINT    NOT NULL DEFAULT 1,
    reset_by           UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    reset_at           TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_totp_status    CHECK (status IN ('PENDING', 'ACTIVE')),
    CONSTRAINT ck_totp_algorithm CHECK (algorithm IN ('SHA1', 'SHA256', 'SHA512')),
    CONSTRAINT ck_totp_digits    CHECK (digits IN (6, 8)),
    CONSTRAINT ck_totp_period    CHECK (period_seconds IN (30, 60)),
    CONSTRAINT ck_totp_failures  CHECK (failed_count >= 0),
    CONSTRAINT ck_totp_keyver    CHECK (key_version >= 1),
    CONSTRAINT ck_totp_pending   CHECK (status <> 'PENDING' OR pending_expires_at IS NOT NULL),
    CONSTRAINT ck_totp_activated CHECK (status <> 'ACTIVE' OR activated_at IS NOT NULL)
);

CREATE INDEX idx_totp_pending_expiry ON user_mfa_totp (pending_expires_at) WHERE status = 'PENDING';

COMMENT ON COLUMN user_mfa_totp.secret_enc IS 'KHONG bao gio vao audit_log.before/after, KHONG vao ban xuat DSAR. Khoa con HKDF mfa.totp.';

-- ============================================================================
-- A8. user_mfa_recovery_code — 10 ma du phong.
-- p11 §11.12.3 (chu mien ma hoa/TOTP) GHI DE p4 §A8: dung HMAC-SHA256(recovery_pepper,
-- code) + pepper_version, KHONG dung BCrypt. Ly do: BCrypt co salt ngau nhien nen
-- KHONG danh UNIQUE(code_hash) duoc, ma chinh chi muc do la thu chan hai admin
-- vo tinh nhan trung ma. HMAC + pepper ngoai DB giu dung co che cua OTP (§11.2.1)
-- va ma kich hoat (§11.7.4) — mot co cho ba, khong phai ba.
-- ============================================================================
CREATE TABLE user_mfa_recovery_code (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- HMAC-SHA256 hex 64 ky tu cua ma 10 ky tu Crockford Base32 (p11 §11.12.3).
    code_hash      CHAR(64)    NOT NULL,
    pepper_version SMALLINT    NOT NULL DEFAULT 1,
    batch_id       UUID        NOT NULL,
    used_at        TIMESTAMPTZ,
    used_ip        INET,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_recovery_code_hash UNIQUE (code_hash),
    CONSTRAINT ck_recovery_code_pepper CHECK (pepper_version >= 1)
);

CREATE INDEX idx_recovery_code_unused ON user_mfa_recovery_code (user_id) WHERE used_at IS NULL;
CREATE INDEX idx_recovery_code_batch  ON user_mfa_recovery_code (user_id, batch_id);

-- ============================================================================
-- A9. user_mfa_reset_request — hang doi reset TOTP theo quy tac hai nguoi.
-- KHONG phai append-only, NHUNG reason/requested_by/requested_at khong duoc UPDATE sau khi tao.
-- ============================================================================
CREATE TABLE user_mfa_reset_request (
    id                   UUID        PRIMARY KEY DEFAULT uuidv7(),
    target_user_id       UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    requested_by         UUID        NOT NULL REFERENCES app_user (id),
    requested_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason               TEXT        NOT NULL,
    approved_by          UUID        REFERENCES app_user (id),
    approved_at          TIMESTAMPTZ,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reject_reason        TEXT,
    expires_at           TIMESTAMPTZ NOT NULL,
    subject_notified_at  TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_mfa_reset_distinct_requester CHECK (requested_by <> target_user_id),
    CONSTRAINT ck_mfa_reset_status     CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_mfa_reset_reason_len CHECK (length(reason) >= 10),
    CONSTRAINT ck_mfa_reset_approver   CHECK (
        approved_by IS NULL OR (approved_by <> requested_by AND approved_by <> target_user_id)),
    CONSTRAINT ck_mfa_reset_approval   CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    CONSTRAINT ck_mfa_reset_reject     CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
    CONSTRAINT ck_mfa_reset_expiry     CHECK (expires_at > requested_at)
);

-- Mot tai khoan chi co MOT yeu cau reset dang mo — chan o DB (p4 §A9).
CREATE UNIQUE INDEX uq_mfa_reset_pending_per_target
    ON user_mfa_reset_request (target_user_id) WHERE status = 'PENDING';
CREATE INDEX idx_mfa_reset_pending_queue
    ON user_mfa_reset_request (status, expires_at) WHERE status = 'PENDING';
CREATE INDEX idx_mfa_reset_requester
    ON user_mfa_reset_request (requested_by, requested_at DESC);

-- ============================================================================
-- p4 §4.6.1: role chay hang ngay `catcheck_app` duoc SELECT/INSERT/UPDATE/DELETE
-- tren bang nghiep vu. V2 (M0) chi GRANT USON schema va ghi TODO sang M1+, nen A1
-- cap quyen cho 9 bang cua minh. Role duoc bao ve bang kiem tra ton tai de migration
-- van chay duoc tren DB cua moi truong (prod/staging deu co role nay).
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON
            app_user, user_identity, user_role, user_device_session,
            email_otp, password_reset, user_mfa_totp,
            user_mfa_recovery_code, user_mfa_reset_request
        TO catcheck_app;
    END IF;
END $$;
