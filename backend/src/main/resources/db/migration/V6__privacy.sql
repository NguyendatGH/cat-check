-- V6: Nhom B — Consent & Phap ly (p4 §4.3 B1..B11, §4.4.3, §4.5.1, §4.9.2).
--
-- 10 bang, ten bang / ten cot / kieu du lieu / CHECK ep dung theo p4 nguyen van.
-- Phu thuoc V5 (app_user). Khong dung retention_run_log (p4 B7: da bi loai bo).
--
-- Append-only bang QUYEN DB (p4 §4.6.2, bat bien I16): consent_record va
-- policy_acknowledgment bi REVOKE UPDATE, DELETE tu catcheck_app. Rut consent =
-- INSERT dong moi status='WITHDRAWN' + supersedes_id, KHONG BAO GIO UPDATE dong cu.

-- ============================================================================
-- 1. consent_purpose — danh muc muc dich xu ly (cau hinh, KHONG hard-code)
-- ============================================================================
CREATE TABLE consent_purpose (
    code                VARCHAR(48)  NOT NULL,
    label_vi            TEXT         NOT NULL,
    label_en            TEXT,
    description_vi      TEXT         NOT NULL,
    description_en      TEXT,
    is_mandatory        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_sensitive_data   BOOLEAN      NOT NULL DEFAULT FALSE,
    default_state       BOOLEAN      NOT NULL DEFAULT FALSE,
    phase               SMALLINT     NOT NULL DEFAULT 1,
    display_order       INT          NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    withdraw_effect     TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_consent_purpose PRIMARY KEY (code),
    CONSTRAINT ck_consent_purpose_phase   CHECK (phase IN (1, 2, 3)),
    -- Bat bien I17: muc dich TUY CHOC khong bao gio tick san (D6.3 NĐ356).
    CONSTRAINT ck_consent_purpose_default_off CHECK (is_mandatory OR default_state = FALSE)
);

-- Render man Trung tam quyen rieng tu mot lan (p4 B3).
CREATE INDEX ix_consent_purpose_display
    ON consent_purpose (active, phase, display_order);

-- ============================================================================
-- 2. policy_version — phien ban van ban chinh sach
-- ============================================================================
CREATE TABLE policy_version (
    id                  UUID         NOT NULL,
    policy_type         VARCHAR(32)  NOT NULL,
    version             VARCHAR(16)  NOT NULL,
    locale              VARCHAR(8)   NOT NULL DEFAULT 'vi',
    title               TEXT         NOT NULL,
    content_md          TEXT,
    content_url         TEXT,
    content_hash        CHAR(64)     NOT NULL,
    summary_of_changes  TEXT,
    requires_reconsent  BOOLEAN      NOT NULL DEFAULT FALSE,
    affected_purposes   VARCHAR(48)[] NOT NULL DEFAULT '{}',
    effective_from      TIMESTAMPTZ  NOT NULL,
    effective_to        TIMESTAMPTZ,
    published_by        UUID         REFERENCES app_user(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_policy_version PRIMARY KEY (id),
    CONSTRAINT ck_policy_version_type   CHECK (policy_type IN (
        'TERMS', 'PRIVACY', 'COOKIE', 'MEDICAL_DISCLAIMER',
        'COMMUNITY_RULES', 'RETURNS', 'PAYMENT')),
    CONSTRAINT ck_policy_version_locale CHECK (locale IN ('vi', 'en')),
    CONSTRAINT ck_policy_version_source CHECK (content_md IS NOT NULL OR content_url IS NOT NULL),
    CONSTRAINT uq_policy_version UNIQUE (policy_type, version, locale)
);

-- Lay ban dang hieu luc: (policy_type, locale, effective_from DESC) — p4 B1.
CREATE INDEX ix_policy_version_effective
    ON policy_version (policy_type, locale, effective_from DESC);

-- ============================================================================
-- 3. consent_record — bang ghi dong y (APPEND-ONLY, p4 B2 + bat bien I16)
-- ============================================================================
CREATE TABLE consent_record (
    id                  UUID         NOT NULL,
    user_id             UUID         NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    purpose_code        VARCHAR(48)  NOT NULL REFERENCES consent_purpose(code),
    status              VARCHAR(16)  NOT NULL,
    policy_version_id   UUID         NOT NULL REFERENCES policy_version(id),
    policy_hash         CHAR(64)     NOT NULL,
    consent_text_hash   CHAR(64)     NOT NULL,
    method              VARCHAR(24)  NOT NULL,
    ui_surface          VARCHAR(64),
    locale              VARCHAR(8)   NOT NULL DEFAULT 'vi',
    supersedes_id       UUID         REFERENCES consent_record(id),
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_address          INET,
    user_agent          TEXT,
    request_id          VARCHAR(64),
    evidence            JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_consent_record PRIMARY KEY (id),
    CONSTRAINT ck_consent_record_status  CHECK (status IN ('GRANTED', 'DENIED', 'WITHDRAWN')),
    CONSTRAINT ck_consent_record_method  CHECK (method IN (
        'WEB_CHECKBOX', 'WEB_TOGGLE', 'EMAIL_LINK', 'ADMIN_ON_BEHALF', 'IMPORT')),
    CONSTRAINT ck_consent_record_locale  CHECK (locale IN ('vi', 'en'))
);

-- Trang thai hien hanh cua mot muc dich (p4 B2).
CREATE INDEX ix_consent_record_user_purpose
    ON consent_record (user_id, purpose_code, occurred_at DESC);
-- Bao cao tuan thu theo muc dich.
CREATE INDEX ix_consent_record_purpose_occurred
    ON consent_record (purpose_code, occurred_at);
CREATE INDEX ix_consent_record_policy_version
    ON consent_record (policy_version_id);

-- Trang thai hien hanh = VIEW, khong phai bang tong hop (p4 B2: view khong bao
-- gio lech voi bang bang chung — lech o day la lech bang chung phap ly).
-- View mang TOAN BO cot de adapter doc duoc dong day du (p4 liet ke hon so cot;
-- DISTINCT ON nghia la "dong moi nhat" nen cac cot con lai cung thuoc dong do).
CREATE VIEW consent_current AS
SELECT DISTINCT ON (user_id, purpose_code)
       id,
       user_id,
       purpose_code,
       status,
       policy_version_id,
       policy_hash,
       consent_text_hash,
       method,
       ui_surface,
       locale,
       supersedes_id,
       occurred_at,
       ip_address,
       user_agent,
       request_id,
       evidence,
       created_at
FROM   consent_record
ORDER  BY user_id, purpose_code, occurred_at DESC;

-- ============================================================================
-- 4. policy_acknowledgment — ghi nhan da doc (KHONG phai consent, p4 B4)
-- ============================================================================
CREATE TABLE policy_acknowledgment (
    id                  UUID         NOT NULL,
    user_id             UUID         NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    policy_version_id   UUID         NOT NULL REFERENCES policy_version(id),
    policy_hash         CHAR(64)     NOT NULL,
    surface             VARCHAR(64),
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ip_address          INET,
    user_agent          TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_policy_acknowledgment PRIMARY KEY (id),
    CONSTRAINT ck_policy_acknowledgment_surface CHECK (surface IN (
        'onboarding_disclaimer', 'result_screen_footer', 'terms_register')),
    -- Mot ban chi ghi mot lan cho mot diem cham (p4 B4). surface NULL khong
    -- bi rang buoc boi UNIQUE (NULL phan biet trong PostgreSQL) — dung y:
    -- cac dong khong gan diem cham khong can rang buoc trung lap.
    CONSTRAINT uq_policy_acknowledgment UNIQUE (user_id, policy_version_id, surface)
);

CREATE INDEX ix_policy_acknowledgment_user_policy
    ON policy_acknowledgment (user_id, policy_version_id);

-- ============================================================================
-- 5. dsar_request — yeu cau thuc hien quyen cua chu the du lieu (p4 B5)
-- ============================================================================
CREATE TABLE dsar_request (
    id                    UUID         NOT NULL,
    public_ref            VARCHAR(20)  NOT NULL,
    user_id               UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    contact_email         CITEXT       NOT NULL,
    request_type          VARCHAR(24)  NOT NULL,
    channel               VARCHAR(16)  NOT NULL,
    status                VARCHAR(24)  NOT NULL DEFAULT 'RECEIVED',
    identity_verified_at  TIMESTAMPTZ,
    identity_method       VARCHAR(32),
    received_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ack_due_at            TIMESTAMPTZ  NOT NULL,
    ack_sent_at           TIMESTAMPTZ,
    fulfil_due_at         TIMESTAMPTZ  NOT NULL,
    extended_to           TIMESTAMPTZ,
    extension_reason      TEXT,
    third_party_involved  BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at          TIMESTAMPTZ,
    rejection_reason      TEXT,
    result_ref            TEXT,
    result_expires_at     TIMESTAMPTZ,
    result_downloaded_at  TIMESTAMPTZ,
    handled_by            UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    pseudonym_id           UUID,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_dsar_request PRIMARY KEY (id),
    CONSTRAINT uq_dsar_request_public_ref UNIQUE (public_ref),
    CONSTRAINT ck_dsar_request_type      CHECK (request_type IN (
        'ACCESS_EXPORT', 'RECTIFY', 'ERASE', 'RESTRICT', 'OBJECT',
        'WITHDRAW_CONSENT', 'PROTECTION_MEASURE', 'COMPLAINT')),
    CONSTRAINT ck_dsar_request_channel   CHECK (channel IN (
        'SELF_SERVICE', 'WEB_FORM', 'EMAIL', 'POST')),
    CONSTRAINT ck_dsar_request_status    CHECK (status IN (
        'RECEIVED', 'IDENTITY_PENDING', 'IN_PROGRESS', 'EXTENDED', 'COMPLETED', 'REJECTED')),
    CONSTRAINT ck_dsar_request_id_method CHECK (identity_method IN (
        'SESSION', 'EMAIL_OTP', 'ID_DOC_MANUAL')),
    CONSTRAINT ck_dsar_request_ack_due   CHECK (ack_due_at > received_at),
    CONSTRAINT ck_dsar_request_fulfil    CHECK (fulfil_due_at > received_at),
    CONSTRAINT ck_dsar_request_extended  CHECK (extended_to IS NULL OR extended_to > fulfil_due_at),
    -- D5 NĐ356: gia han bat buoc co ly do.
    CONSTRAINT ck_dsar_request_extension_reason
        CHECK (extended_to IS NULL OR extension_reason IS NOT NULL),
    -- D13.3/D14.5: tu choi bat buoc neua "ly do chinh dang".
    CONSTRAINT ck_dsar_request_rejection_reason
        CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- DsarSlaMonitorJob quet yeu cau sap qua han (p4 B5).
CREATE INDEX ix_dsar_request_due
    ON dsar_request (status, fulfil_due_at);
-- Lich su yeu cau cua toi o /account/privacy (partial — p4 B5).
CREATE INDEX ix_dsar_request_user_received
    ON dsar_request (user_id, received_at DESC)
    WHERE user_id IS NOT NULL;
-- Mot tai khoan chi co MOT yeu cau xoa dang mo (p4 B5, p8 DELETION_ALREADY_REQUESTED).
CREATE UNIQUE INDEX uq_dsar_request_open_erase
    ON dsar_request (user_id)
    WHERE request_type = 'ERASE' AND status NOT IN ('COMPLETED', 'REJECTED');

-- ============================================================================
-- 6. retention_policy — thoi han luu la CAU HINH (p4 B6, p15 REQ-RET-03)
-- ============================================================================
CREATE TABLE retention_policy (
    code                    VARCHAR(48)  NOT NULL,
    data_inventory_code     VARCHAR(8),
    target_table            VARCHAR(64)  NOT NULL,
    retention_days          INT,
    anchor_column           VARCHAR(64)  NOT NULL,
    action_on_expiry        VARCHAR(16)  NOT NULL,
    job_name                VARCHAR(64),
    safety_threshold_percent INT         NOT NULL DEFAULT 20,
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    legal_basis             TEXT,
    updated_by              UUID         REFERENCES app_user(id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_retention_policy PRIMARY KEY (code),
    CONSTRAINT ck_retention_policy_days      CHECK (retention_days IS NULL OR retention_days > 0),
    CONSTRAINT ck_retention_policy_action    CHECK (action_on_expiry IN (
        'HARD_DELETE', 'ANONYMIZE', 'ARCHIVE', 'MASK')),
    CONSTRAINT ck_retention_policy_threshold CHECK (safety_threshold_percent BETWEEN 1 AND 100)
);

CREATE INDEX ix_retention_policy_job
    ON retention_policy (enabled, job_name);

-- ============================================================================
-- 7. holiday_calendar — ngay le de tinh SLA theo ngay lam viec (p4 B8)
-- ============================================================================
CREATE TABLE holiday_calendar (
    holiday_date  DATE         NOT NULL,
    country_code  CHAR(2)      NOT NULL,
    name_vi       TEXT         NOT NULL,
    source        VARCHAR(24)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_holiday_calendar PRIMARY KEY (country_code, holiday_date),
    CONSTRAINT ck_holiday_calendar_source CHECK (source IN ('OFFICIAL', 'COMPANY'))
);

-- ============================================================================
-- 8. data_inventory_item — bang kiem ke du lieu ca nhan D1..D21 (p4 B10)
-- ============================================================================
CREATE TABLE data_inventory_item (
    code                    VARCHAR(8)   NOT NULL,
    category_vi             TEXT         NOT NULL,
    description_vi          TEXT         NOT NULL,
    description_en          TEXT,
    sensitivity             VARCHAR(12)  NOT NULL,
    legal_basis             VARCHAR(16)  NOT NULL,
    purpose_codes           VARCHAR(48)[] NOT NULL DEFAULT '{}',
    retention_policy_code   VARCHAR(48),
    storage_location        TEXT         NOT NULL,
    cross_border            BOOLEAN      NOT NULL DEFAULT FALSE,
    recipient               TEXT,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_data_inventory_item PRIMARY KEY (code),
    CONSTRAINT ck_data_inventory_item_sensitivity CHECK (sensitivity IN ('BASIC', 'SENSITIVE')),
    CONSTRAINT ck_data_inventory_item_legal_basis CHECK (legal_basis IN (
        'CONSENT', 'CONTRACT', 'LEGAL_OBLIGATION', 'VITAL_INTEREST'))
);

CREATE INDEX ix_data_inventory_item_active
    ON data_inventory_item (active, sensitivity);

-- ============================================================================
-- 9. security_incident — ho so su co lo/mat du lieu (p4 B9)
-- ============================================================================
CREATE TABLE security_incident (
    id                      UUID         NOT NULL,
    public_ref              VARCHAR(20)  NOT NULL,
    severity                VARCHAR(12)  NOT NULL,
    category                VARCHAR(32)  NOT NULL,
    summary                 TEXT         NOT NULL,
    affected_subject_count  INT,
    affected_data_codes     VARCHAR(8)[] NOT NULL DEFAULT '{}',
    detected_at             TIMESTAMPTZ  NOT NULL,
    classified_at           TIMESTAMPTZ,
    contained_at            TIMESTAMPTZ,
    authority_notified_at   TIMESTAMPTZ,
    subjects_notified_at    TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    retain_until            TIMESTAMPTZ,
    handled_by              UUID         REFERENCES app_user(id),
    report_ref              TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_security_incident PRIMARY KEY (id),
    CONSTRAINT uq_security_incident_public_ref UNIQUE (public_ref),
    CONSTRAINT ck_security_incident_severity CHECK (severity IN (
        'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_security_incident_category CHECK (category IN (
        'DATA_BREACH', 'DATA_LOSS', 'UNAUTHORIZED_ACCESS', 'AVAILABILITY', 'OTHER'))
);

CREATE INDEX ix_security_incident_severity_detected
    ON security_incident (severity, detected_at DESC);
-- Job retention khong duoc xoa truoc retain_until (partial — p4 B9).
CREATE INDEX ix_security_incident_retain
    ON security_incident (retain_until)
    WHERE resolved_at IS NOT NULL;

-- ============================================================================
-- 10. user_activity_log — log hanh vi san pham (p4 B11)
-- ============================================================================
CREATE TABLE user_activity_log (
    id            UUID         NOT NULL,
    user_id       UUID         REFERENCES app_user(id) ON DELETE CASCADE,
    event_code    VARCHAR(48)  NOT NULL,
    props         JSONB        NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_activity_log PRIMARY KEY (id)
);

CREATE INDEX ix_user_activity_log_user_occurred
    ON user_activity_log (user_id, occurred_at DESC);
CREATE INDEX ix_user_activity_log_event_occurred
    ON user_activity_log (event_code, occurred_at);
-- Bang ghi rat nhieu: BRIN tren occurred_at (p4 B11, §4.7).
CREATE INDEX ix_user_activity_log_brin
    ON user_activity_log USING brin (occurred_at);

-- ============================================================================
-- 11. Hai vong lap FK retention_policy <-> data_inventory_item (p4 §4.9.3)
--     xu ly tung minh: tao bang KHONG co FK, roi ALTER ADD CONSTRAINT.
-- ============================================================================
ALTER TABLE retention_policy
    ADD CONSTRAINT fk_retention_policy_inventory
    FOREIGN KEY (data_inventory_code) REFERENCES data_inventory_item(code);

ALTER TABLE data_inventory_item
    ADD CONSTRAINT fk_data_inventory_item_retention
    FOREIGN KEY (retention_policy_code) REFERENCES retention_policy(code);

-- ============================================================================
-- 12. Sequence sinh public_ref (DSAR-2026-000123 / INC-2026-0007 — p4 B5/B9)
-- ============================================================================
CREATE SEQUENCE dsar_request_public_ref_seq;
CREATE SEQUENCE security_incident_public_ref_seq;

GRANT USAGE ON SEQUENCE dsar_request_public_ref_seq TO catcheck_app;
GRANT USAGE ON SEQUENCE security_incident_public_ref_seq TO catcheck_app;

-- ============================================================================
-- 13. Append-only bang QUYEN DB (p4 §4.6.2, bat bien I16)
--     consent_record + policy_acknowledgment: REVOKE UPDATE, DELETE.
--     policy_version + security_incident: REVOKE DELETE (van UPDATE duoc — p4 §4.1.2).
-- ============================================================================
REVOKE UPDATE, DELETE ON consent_record FROM catcheck_app;
REVOKE UPDATE, DELETE ON policy_acknowledgment FROM catcheck_app;
REVOKE DELETE ON policy_version FROM catcheck_app;
REVOKE DELETE ON security_incident FROM catcheck_app;

-- catcheck_job / catcheck_readonly chua ton tai o V2 (TODO cua V2) — bo trong DO
-- block co dieu kien nhu V10__credit.sql: khi W3 bo sung role, REVOKE tu dong co
-- hieu luc ma khong can sua checksum.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_job') THEN
        EXECUTE 'REVOKE UPDATE, DELETE ON consent_record FROM catcheck_job';
        EXECUTE 'REVOKE UPDATE, DELETE ON policy_acknowledgment FROM catcheck_job';
        EXECUTE 'REVOKE DELETE ON policy_version FROM catcheck_job';
        EXECUTE 'REVOKE DELETE ON security_incident FROM catcheck_job';
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_readonly') THEN
        EXECUTE 'REVOKE UPDATE, DELETE ON consent_record FROM catcheck_readonly';
        EXECUTE 'REVOKE UPDATE, DELETE ON policy_acknowledgment FROM catcheck_readonly';
        EXECUTE 'REVOKE DELETE ON policy_version FROM catcheck_readonly';
        EXECUTE 'REVOKE DELETE ON security_incident FROM catcheck_readonly';
    END IF;
END
$$;
