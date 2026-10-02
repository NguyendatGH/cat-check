-- V11: Quet cat & phan tich mau - module `scan` (p7 §7.2.3).
--
-- Nguon dac ta: spec/parts/p4-domain-model-erd.md §4.9.2 (dong V11) - "scan, scan_image,
-- scan_analysis, scan_analysis_recompute, scan_reassignment; FK scan.credit_ledger_id,
-- scan.current_analysis_id (DEFERRABLE), cat_clinical_sign_report.scan_id, cat_note.scan_id",
-- phu thuoc V8 (cat), V9 (colorchart), V10 (credit). Chi tiet tung bang: p4 §4.9.2 "Nhom D -
-- Scan & Analysis" (D1-D4) va D13.
--
-- Moi cot thoi gian la TIMESTAMPTZ, cot ngay la DATE (p4 §4.1.2). Moi cot id la UUID
-- DEFAULT uuidv7() (p4 §4.1.1). Moi enum luu bang VARCHAR + CHECK, KHONG dung ENUM (p4 §4.1.3).
-- KHONG tao trigger set_updated_at o day - p4 §4.9.2 giao V16 cho W3.

-- ===========================================================================
-- D1. scan - mot lan quet
-- ===========================================================================
CREATE TABLE IF NOT EXISTS scan
(
    id                   UUID          NOT NULL DEFAULT uuidv7(),
    user_id              UUID          NOT NULL,
    cat_id               UUID,
    assignment           VARCHAR(20)   NOT NULL DEFAULT 'UNASSIGNED',
    captured_at          TIMESTAMPTZ   NOT NULL,
    capture_source       VARCHAR(16)   NOT NULL DEFAULT 'CAMERA',
    device_hint          VARCHAR(120),
    is_trial             BOOLEAN       NOT NULL DEFAULT false,
    store_image          BOOLEAN       NOT NULL DEFAULT false,
    store_image_reason   VARCHAR(24),
    status               VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    failure_code         VARCHAR(48),
    current_analysis_id  UUID,
    idempotency_key      VARCHAR(64)   NOT NULL,
    credit_ledger_id     UUID,
    reassign_count       SMALLINT      NOT NULL DEFAULT 0,
    disputed_at          TIMESTAMPTZ,
    disputed_note        TEXT,
    chart_id             UUID,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan PRIMARY KEY (id),
    CONSTRAINT ck_scan_assignment CHECK (assignment IN ('UNASSIGNED', 'ASSIGNED', 'SHARED_UNKNOWN')),
    -- p4 D1: CHECK ((assignment='ASSIGNED') = (cat_id IS NOT NULL))
    CONSTRAINT ck_scan_assignment_cat CHECK ((assignment = 'ASSIGNED') = (cat_id IS NOT NULL)),
    CONSTRAINT ck_scan_capture_source CHECK (capture_source IN ('CAMERA', 'GALLERY')),
    -- p4 D1: CHECK (NOT is_trial OR credit_ledger_id IS NULL)
    CONSTRAINT ck_scan_trial_no_ledger CHECK (NOT is_trial OR credit_ledger_id IS NULL),
    -- p4 D1: CHECK (NOT (is_trial AND store_image))
    CONSTRAINT ck_scan_trial_no_image CHECK (NOT (is_trial AND store_image)),
    CONSTRAINT ck_scan_store_image_reason CHECK (store_image_reason IS NULL
        OR store_image_reason IN ('TRIAL', 'CONSENT_OFF', 'PLAN_OFF', 'INCONCLUSIVE')),
    -- p4 D1: CHECK (store_image OR store_image_reason IS NOT NULL)
    CONSTRAINT ck_scan_store_image_needs_reason CHECK (store_image OR store_image_reason IS NOT NULL),
    CONSTRAINT ck_scan_status CHECK (status IN ('PENDING', 'ANALYZED', 'FAILED', 'DISCARDED')),
    CONSTRAINT ck_scan_reassign_count CHECK (reassign_count BETWEEN 0 AND 3),
    CONSTRAINT ck_scan_disputed_note CHECK (disputed_note IS NULL OR length(disputed_note) <= 500),
    -- p4 D1: server kep captured_at trong ±2 gio so voi now() luc ghi; o DB chi chan qua khu
    -- tuong lai xa (client gui sai gio) - phep tru 2 gio + luoi an toan cho do lech dong ho.
    CONSTRAINT ck_scan_captured_at CHECK (captured_at <= now() + interval '2 hours'),
    CONSTRAINT uq_scan_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- p4 D1 danh sach index.
CREATE INDEX IF NOT EXISTS idx_scan_cat_captured
    ON scan (cat_id, captured_at DESC)
    WHERE deleted_at IS NULL AND status = 'ANALYZED';
CREATE INDEX IF NOT EXISTS idx_scan_user_captured
    ON scan (user_id, captured_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_scan_pending
    ON scan (status, created_at)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_scan_unassigned
    ON scan (user_id)
    WHERE assignment = 'UNASSIGNED' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_scan_shared_unknown
    ON scan (user_id, captured_at DESC)
    WHERE assignment = 'SHARED_UNKNOWN' AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_scan_disputed
    ON scan (disputed_at)
    WHERE disputed_at IS NOT NULL;

COMMENT ON TABLE scan IS 'Mot lan quet cat (module `scan`, p4 D1). Hanh dong cua nguoi dung - tach khoi ket qua phan tich (scan_analysis).';
COMMENT ON COLUMN scan.is_trial IS 'Nghia hep: scan cua tai khoan CHUA TUNG kich hoat bat ky goi nao (p5 R6). KHONG dung de suy ra co luu anh hay khong.';
COMMENT ON COLUMN scan.store_image IS 'Quyet dinh DUY NHAT ve viec co luu anh hay khong (p4 D1).';
COMMENT ON COLUMN scan.credit_ledger_id IS 'FK -> credit_ledger(id) (module `credit`). NULL neu trial, INCONCLUSIVE, hoac chua tru.';
COMMENT ON COLUMN scan.chart_id IS 'FK -> color_chart(id) (module `colorchart`). Bang mau co hieu luc luc quet.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_user') THEN
        ALTER TABLE scan ADD CONSTRAINT fk_scan_user
            FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_cat') THEN
        ALTER TABLE scan ADD CONSTRAINT fk_scan_cat
            FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_credit_ledger') THEN
        ALTER TABLE scan ADD CONSTRAINT fk_scan_credit_ledger
            FOREIGN KEY (credit_ledger_id) REFERENCES credit_ledger (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_chart') THEN
        ALTER TABLE scan ADD CONSTRAINT fk_scan_chart
            FOREIGN KEY (chart_id) REFERENCES color_chart (id);
    END IF;
END
$$;

-- ===========================================================================
-- D2. scan_image - anh goc
-- ===========================================================================
CREATE TABLE IF NOT EXISTS scan_image
(
    id                UUID          NOT NULL DEFAULT uuidv7(),
    scan_id           UUID          NOT NULL,
    storage_provider  VARCHAR(16)   NOT NULL DEFAULT 'LOCAL',
    storage_key       TEXT,
    content_type      VARCHAR(40)   NOT NULL,
    bytes             BIGINT        NOT NULL,
    width             INT           NOT NULL,
    height            INT           NOT NULL,
    -- VARCHAR(64) khong phai CHAR(64) nhu p4 D2 ghi: Hibernate validate schema luon mong VARCHAR
    -- cho truong String cua entity ScanImage du columnDefinition ghi gi (xac nhan bang
    -- ApplicationContextSmokeTest qua Testcontainers that) - xem docs/handovers/A6.md.
    checksum_sha256   VARCHAR(64),
    expires_at        TIMESTAMPTZ   NOT NULL,
    deleted_at        TIMESTAMPTZ,
    delete_reason     VARCHAR(24),
    migrated_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan_image PRIMARY KEY (id),
    CONSTRAINT uq_scan_image_scan UNIQUE (scan_id),
    CONSTRAINT ck_scan_image_storage_provider CHECK (storage_provider IN ('LOCAL', 'CLOUDINARY')),
    CONSTRAINT ck_scan_image_bytes CHECK (bytes > 0 AND bytes <= 8388608),
    CONSTRAINT ck_scan_image_width CHECK (width > 0),
    CONSTRAINT ck_scan_image_height CHECK (height > 0),
    CONSTRAINT ck_scan_image_delete_reason CHECK (delete_reason IS NULL
        OR delete_reason IN ('RETENTION', 'USER_REQUEST', 'ACCOUNT_DELETION')),
    CONSTRAINT ck_scan_image_expires_after_created CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_scan_image_expires
    ON scan_image (expires_at)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_scan_image_local_alive
    ON scan_image (storage_provider)
    WHERE storage_provider = 'LOCAL' AND deleted_at IS NULL;

COMMENT ON TABLE scan_image IS 'Anh goc cua mot lan quet (p4 D2). Retention 14 ngay - xoa cung file, giu tombstone dong.';
COMMENT ON COLUMN scan_image.storage_key IS 'Khoa luu trong storage, KHONG luu URL. Set NULL sau khi xoa file.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_image_scan') THEN
        ALTER TABLE scan_image ADD CONSTRAINT fk_scan_image_scan
            FOREIGN KEY (scan_id) REFERENCES scan (id) ON DELETE CASCADE;
    END IF;
END
$$;

-- ===========================================================================
-- D3. scan_analysis - ket qua phan tich
-- ===========================================================================
CREATE TABLE IF NOT EXISTS scan_analysis
(
    id                          UUID           NOT NULL DEFAULT uuidv7(),
    scan_id                     UUID           NOT NULL,
    is_current                  BOOLEAN        NOT NULL DEFAULT true,
    ph_value                    NUMERIC(3, 1),
    ph_low                      NUMERIC(3, 1),
    ph_high                     NUMERIC(3, 1),
    classification              VARCHAR(24)    NOT NULL,
    band_id                     UUID,
    confidence                  NUMERIC(4, 3)  NOT NULL,
    near_boundary               BOOLEAN        NOT NULL DEFAULT false,
    lab_l                       NUMERIC(6, 3),
    lab_a                       NUMERIC(6, 3),
    lab_b                       NUMERIC(6, 3),
    lab_spread_de00             NUMERIC(5, 2),
    blob_count                  INT,
    indicator_pixel_ratio       NUMERIC(5, 4),
    substrate_lab_l             NUMERIC(6, 3),
    substrate_lab_a             NUMERIC(6, 3),
    substrate_lab_b             NUMERIC(6, 3),
    delta_e_min                 NUMERIC(6, 3),
    perp_residual_de00          NUMERIC(6, 3),
    match_percent               INT,
    matched_point_id            UUID,
    matched_segment_k           INT,
    matched_t                   NUMERIC(5, 4),
    calibration_method          VARCHAR(20)    NOT NULL,
    calibration_residual_de00   NUMERIC(6, 3),
    calibration                 JSONB,
    card_layout_id              UUID,
    quality_metrics             JSONB,
    quality_flags                JSONB         NOT NULL DEFAULT '[]',
    chart_id                    UUID,
    chart_version                INT,
    engine_version               VARCHAR(24)   NOT NULL,
    processing_ms                INT,
    computed_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    recompute_of                 UUID,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan_analysis PRIMARY KEY (id),
    CONSTRAINT ck_scan_analysis_ph_value CHECK (ph_value IS NULL OR ph_value BETWEEN 3.0 AND 11.0),
    CONSTRAINT ck_scan_analysis_ph_low CHECK (ph_low IS NULL OR ph_value IS NULL OR ph_low <= ph_value),
    CONSTRAINT ck_scan_analysis_ph_high CHECK (ph_high IS NULL OR ph_value IS NULL OR ph_high >= ph_value),
    CONSTRAINT ck_scan_analysis_classification CHECK (classification IN
        ('IN_RANGE', 'SLIGHTLY_LOW', 'SLIGHTLY_HIGH', 'LOW', 'HIGH', 'INCONCLUSIVE')),
    CONSTRAINT ck_scan_analysis_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_scan_analysis_spread CHECK (lab_spread_de00 IS NULL OR lab_spread_de00 >= 0),
    CONSTRAINT ck_scan_analysis_blob_count CHECK (blob_count IS NULL OR blob_count >= 0),
    CONSTRAINT ck_scan_analysis_indicator_ratio CHECK (indicator_pixel_ratio IS NULL
        OR indicator_pixel_ratio BETWEEN 0 AND 1),
    CONSTRAINT ck_scan_analysis_delta_e_min CHECK (delta_e_min IS NULL OR delta_e_min >= 0),
    CONSTRAINT ck_scan_analysis_perp_residual CHECK (perp_residual_de00 IS NULL OR perp_residual_de00 >= 0),
    CONSTRAINT ck_scan_analysis_match_percent CHECK (match_percent IS NULL OR match_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_scan_analysis_matched_t CHECK (matched_t IS NULL OR matched_t BETWEEN 0 AND 1),
    CONSTRAINT ck_scan_analysis_calibration_method CHECK (calibration_method IN
        ('CARD_CCM', 'SUBSTRATE_WB', 'NONE')),
    CONSTRAINT ck_scan_analysis_calibration_residual CHECK (calibration_residual_de00 IS NULL
        OR calibration_residual_de00 >= 0),
    CONSTRAINT ck_scan_analysis_processing_ms CHECK (processing_ms IS NULL OR processing_ms >= 0)
);

-- p4 D3: tối đa 1 is_current=true mỗi scan.
CREATE UNIQUE INDEX IF NOT EXISTS uq_scan_analysis_current
    ON scan_analysis (scan_id)
    WHERE is_current;
CREATE INDEX IF NOT EXISTS idx_scan_analysis_scan_computed
    ON scan_analysis (scan_id, computed_at DESC);
CREATE INDEX IF NOT EXISTS idx_scan_analysis_classification_current
    ON scan_analysis (classification, computed_at DESC)
    WHERE is_current;
CREATE INDEX IF NOT EXISTS idx_scan_analysis_engine_chart
    ON scan_analysis (engine_version, chart_version);
CREATE INDEX IF NOT EXISTS idx_scan_analysis_quality_flags
    ON scan_analysis USING gin (quality_flags jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_scan_analysis_low_confidence
    ON scan_analysis (confidence)
    WHERE is_current AND confidence < 0.55;

COMMENT ON TABLE scan_analysis IS 'Ket qua phan tich pipeline mau (p4 D3). Nhieu dong/scan (tinh lai khi doi bang mau) - chi 1 dong is_current=true.';
COMMENT ON COLUMN scan_analysis.lab_l IS 'Bat buoc cho moi ket qua khong INCONCLUSIVE. Nguon cho backfill (M7).';
COMMENT ON COLUMN scan_analysis.band_id IS 'FK -> ph_classification_band(id). NULL o M3: ChartCatalog.findGlobalBands() chua tra UUID goc (xem docs/handovers/A6.md).';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_scan') THEN
        ALTER TABLE scan_analysis ADD CONSTRAINT fk_scan_analysis_scan
            FOREIGN KEY (scan_id) REFERENCES scan (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_band') THEN
        ALTER TABLE scan_analysis ADD CONSTRAINT fk_scan_analysis_band
            FOREIGN KEY (band_id) REFERENCES ph_classification_band (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_matched_point') THEN
        ALTER TABLE scan_analysis ADD CONSTRAINT fk_scan_analysis_matched_point
            FOREIGN KEY (matched_point_id) REFERENCES color_chart_point (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_card_layout') THEN
        ALTER TABLE scan_analysis ADD CONSTRAINT fk_scan_analysis_card_layout
            FOREIGN KEY (card_layout_id) REFERENCES reference_card_layout (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_chart') THEN
        ALTER TABLE scan_analysis ADD CONSTRAINT fk_scan_analysis_chart
            FOREIGN KEY (chart_id) REFERENCES color_chart (id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_recompute_of') THEN
        ALTER TABLE scan_analysis ADD CONSTRAINT fk_scan_analysis_recompute_of
            FOREIGN KEY (recompute_of) REFERENCES scan_analysis (id);
    END IF;
    -- p4 D1: scan.current_analysis_id -> scan_analysis(id), DEFERRABLE (vong lap FK: scan can
    -- scan_analysis ton tai truoc, nhung scan_analysis.scan_id can scan ton tai truoc - p4 §4.9.3).
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_current_analysis') THEN
        ALTER TABLE scan ADD CONSTRAINT fk_scan_current_analysis
            FOREIGN KEY (current_analysis_id) REFERENCES scan_analysis (id)
            DEFERRABLE INITIALLY DEFERRED;
    END IF;
END
$$;

-- ===========================================================================
-- D4. scan_analysis_recompute - nhat ky tinh lai (nhe). Chi tao bang o M3 - job backfill
-- thuc (M7, ngoai pham vi MVP) se ghi vao day. Xem docs/handovers/A6.md.
-- ===========================================================================
CREATE TABLE IF NOT EXISTS scan_analysis_recompute
(
    id                     UUID          NOT NULL DEFAULT uuidv7(),
    scan_analysis_id       UUID          NOT NULL,
    chart_id               UUID          NOT NULL,
    chart_version          INT           NOT NULL,
    ph_value               NUMERIC(3, 1),
    classification         VARCHAR(24),
    confidence             NUMERIC(4, 3),
    delta_ph               NUMERIC(4, 2),
    flipped_classification BOOLEAN       NOT NULL DEFAULT false,
    job_id                 UUID,
    computed_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan_analysis_recompute PRIMARY KEY (id),
    CONSTRAINT ck_scan_analysis_recompute_classification CHECK (classification IS NULL OR classification IN
        ('IN_RANGE', 'SLIGHTLY_LOW', 'SLIGHTLY_HIGH', 'LOW', 'HIGH', 'INCONCLUSIVE'))
);

CREATE INDEX IF NOT EXISTS idx_scan_analysis_recompute_job
    ON scan_analysis_recompute (job_id, flipped_classification);
CREATE INDEX IF NOT EXISTS idx_scan_analysis_recompute_analysis
    ON scan_analysis_recompute (scan_analysis_id);

COMMENT ON TABLE scan_analysis_recompute IS 'Nhat ky tinh lai khi backfill bang mau moi o che do chi ghi nhan (p4 D4). M7 - ngoai pham vi MVP.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_recompute_analysis') THEN
        ALTER TABLE scan_analysis_recompute ADD CONSTRAINT fk_scan_analysis_recompute_analysis
            FOREIGN KEY (scan_analysis_id) REFERENCES scan_analysis (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_analysis_recompute_chart') THEN
        ALTER TABLE scan_analysis_recompute ADD CONSTRAINT fk_scan_analysis_recompute_chart
            FOREIGN KEY (chart_id) REFERENCES color_chart (id);
    END IF;
END
$$;

-- ===========================================================================
-- D13. scan_reassignment - nhat ky doi meo cua mot lan quet (append-only, p4 §4.1.2)
-- ===========================================================================
CREATE TABLE IF NOT EXISTS scan_reassignment
(
    id               UUID          NOT NULL DEFAULT uuidv7(),
    scan_id          UUID          NOT NULL,
    from_cat_id      UUID,
    to_cat_id        UUID,
    from_assignment  VARCHAR(20)   NOT NULL,
    to_assignment    VARCHAR(20)   NOT NULL,
    changed_by       UUID          NOT NULL,
    changed_by_role  VARCHAR(32)   NOT NULL,
    reason           TEXT,
    changed_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_scan_reassignment PRIMARY KEY (id),
    CONSTRAINT ck_scan_reassignment_from_assignment CHECK (from_assignment IN
        ('UNASSIGNED', 'ASSIGNED', 'SHARED_UNKNOWN')),
    CONSTRAINT ck_scan_reassignment_to_assignment CHECK (to_assignment IN
        ('UNASSIGNED', 'ASSIGNED', 'SHARED_UNKNOWN')),
    -- p4 D13: reason bat buoc khi nguoi doi la admin (p15 REQ-AUD-03).
    CONSTRAINT ck_scan_reassignment_reason CHECK (changed_by_role = 'USER' OR reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_scan_reassignment_scan
    ON scan_reassignment (scan_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_scan_reassignment_to_cat
    ON scan_reassignment (to_cat_id, changed_at DESC);

COMMENT ON TABLE scan_reassignment IS 'Nhat ky doi meo cua mot lan quet (p4 D13). Append-only - khong updated_at, khong trigger.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_reassignment_scan') THEN
        ALTER TABLE scan_reassignment ADD CONSTRAINT fk_scan_reassignment_scan
            FOREIGN KEY (scan_id) REFERENCES scan (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_reassignment_from_cat') THEN
        ALTER TABLE scan_reassignment ADD CONSTRAINT fk_scan_reassignment_from_cat
            FOREIGN KEY (from_cat_id) REFERENCES cat (id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_reassignment_to_cat') THEN
        ALTER TABLE scan_reassignment ADD CONSTRAINT fk_scan_reassignment_to_cat
            FOREIGN KEY (to_cat_id) REFERENCES cat (id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_scan_reassignment_changed_by') THEN
        ALTER TABLE scan_reassignment ADD CONSTRAINT fk_scan_reassignment_changed_by
            FOREIGN KEY (changed_by) REFERENCES app_user (id);
    END IF;
END
$$;

-- ===========================================================================
-- Bo sung cot scan_id con thieu tren cat_note / cat_clinical_sign_report (module `cat`, V8).
-- Bang `scan` chua ton tai luc V8 chay nen A3 khong the tao FK - p4 §4.9.2 giao viec nay cho V11.
-- Xem docs/handovers/A3.md muc "V11 can bo sung gi".
-- ===========================================================================
ALTER TABLE cat_note ADD COLUMN IF NOT EXISTS scan_id UUID;
COMMENT ON COLUMN cat_note.scan_id IS 'Gan ghi chu vao mot lan quet cu the (p4 C4). FK them o V11 vi bang scan chua ton tai luc V8.';

CREATE INDEX IF NOT EXISTS idx_cat_note_scan
    ON cat_note (scan_id)
    WHERE scan_id IS NOT NULL AND deleted_at IS NULL;

ALTER TABLE cat_clinical_sign_report ADD COLUMN IF NOT EXISTS scan_id UUID;
COMMENT ON COLUMN cat_clinical_sign_report.scan_id IS 'Lan quet ma user khai kem; NULL khi khai tu khao sat ban dau (p4 C5). FK them o V11.';

CREATE INDEX IF NOT EXISTS idx_cat_clinical_sign_report_scan
    ON cat_clinical_sign_report (scan_id)
    WHERE scan_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_note_scan') THEN
        ALTER TABLE cat_note ADD CONSTRAINT fk_cat_note_scan
            FOREIGN KEY (scan_id) REFERENCES scan (id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_clinical_sign_report_scan') THEN
        ALTER TABLE cat_clinical_sign_report ADD CONSTRAINT fk_cat_clinical_sign_report_scan
            FOREIGN KEY (scan_id) REFERENCES scan (id) ON DELETE SET NULL;
    END IF;
END
$$;
