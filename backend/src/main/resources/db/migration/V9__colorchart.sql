-- V9: Color chart - bang mau pH va cau hinh hieu chuan (module `colorchart`, A5).
--
-- Nguon dac ta: spec/parts/p4-domain-model-erd.md §4.9.2 (dong V9) - "reference_card_layout,
-- color_chart, color_chart_point, ph_classification_band, litter_substrate_profile,
-- chart_calibration_job", phu thuoc V5 (app_user).
--
-- ⚠ LUU Y VE PHAN BO SO HUUU: A5 so huu FILE nay. Module `scan` (A6) doc bang qua port,
-- KHONG dung entity cua module nay (R6 + p7 §7.2.3: moi bang thuoc dung mot module).
--
-- ⚠ LUU Y VE SEED: p4 §4.9.2 liet ke seed bang file R__ repeatable, nhung ORCHESTRATOR §3.2
-- chi cho phep A5 tao DUY NHAT file V9__colorchart.sql. Do do seed duoc dat TRONG file nay
-- voi INSERT ... ON CONFLICT DO NOTHING (idempotent, chay lai duoc). ID seed la UUID co
-- dinh (khong phai uuidv7()) de ON CONFLICT (id) khoi tao duoc dinh tuyen — p4 §4.1.1
-- cho phep dung ID tuy y cho seed data.
--
-- Moi bang KHONG co trigger `set_updated_at`: p4 §4.9.2 giao V16 cho W3.
-- Moi cot thoi gian la TIMESTAMPTZ (p4 §4.1.2). Moi cot ID la UUID native voi
-- DEFAULT uuidv7() (PostgreSQL 18.6 co ham native, p4 §4.1.1).
-- Moi enum luu bang VARCHAR + CHECK, KHONG dung PostgreSQL ENUM (p4 §4.1.3).

-- ===========================================================================
-- reference_card layout - layout the mau tham chieu (p4 D8).
-- JSONB dung cho cau truc long sau, chi doc nguyen khi khoi tao detector.
-- ===========================================================================
CREATE TABLE IF NOT EXISTS reference_card_layout
(
    id         UUID         NOT NULL,
    code       VARCHAR(32)  NOT NULL,
    version    INT          NOT NULL,
    layout     JSONB        NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_reference_card_layout PRIMARY KEY (id),
    CONSTRAINT uq_reference_card_layout_code_version UNIQUE (code, version),
    CONSTRAINT ck_reference_card_layout_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_reference_card_layout_version CHECK (version >= 1)
);

CREATE INDEX IF NOT EXISTS idx_reference_card_layout_active
    ON reference_card_layout (active) WHERE active;

COMMENT ON TABLE reference_card_layout IS 'Layout the mau tham chieu (p4 D8).';
COMMENT ON COLUMN reference_card_layout.layout IS 'JSONB: kich thuoc vat ly, canonical px, marker (ArUco/QR), danh sach patch voi rect/ref_srgb/ref_lab.';

-- ===========================================================================
-- chart_calibration_job - job hieu chuan bang mau (p4 D10).
-- Tao TRUOC color_chart vi color_chart co FK toi bang nay (FK them bang ALTER o duoi).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS chart_calibration_job
(
    id                  UUID         NOT NULL,
    requested_by        UUID         NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    source_chart_id     UUID,
    produced_chart_id   UUID,
    sample_image_count  INT          NOT NULL DEFAULT 0,
    result_summary      JSONB,
    failure_reason      TEXT,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_chart_calibration_job PRIMARY KEY (id),
    CONSTRAINT ck_chart_calibration_job_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'REVIEW', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_chart_calibration_job_sample_count CHECK (sample_image_count >= 0),
    CONSTRAINT fk_chart_calibration_job_requester FOREIGN KEY (requested_by)
        REFERENCES app_user (id)
);

CREATE INDEX IF NOT EXISTS idx_chart_calibration_job_status_created
    ON chart_calibration_job (status, created_at DESC);

COMMENT ON TABLE chart_calibration_job IS 'Job hieu chuan bang mau (p4 D10).';

-- ===========================================================================
-- color_chart - bang mau pH (cau hinh, co version) (p4 D5).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS color_chart
(
    id                  UUID         NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    version             INT          NOT NULL DEFAULT 1,
    name                TEXT         NOT NULL,
    product_line        VARCHAR(16),
    production_batch    VARCHAR(64),
    card_layout_id      UUID,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    is_placeholder      BOOLEAN      NOT NULL DEFAULT true,
    illuminant         VARCHAR(8)   NOT NULL DEFAULT 'D65',
    observer            VARCHAR(8)   NOT NULL DEFAULT '2',
    delta_e_formula     VARCHAR(16)  NOT NULL DEFAULT 'CIEDE2000',
    params              JSONB        NOT NULL DEFAULT '{"kL":2,"kC":1,"kH":1,"matchScaleDE":15}'::jsonb,
    source              VARCHAR(16)  NOT NULL DEFAULT 'MANUAL_HEX',
    calibration_job_id  UUID,
    effective_from      TIMESTAMPTZ,
    effective_to        TIMESTAMPTZ,
    published_at        TIMESTAMPTZ,
    created_by          UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_color_chart PRIMARY KEY (id),
    CONSTRAINT uq_color_chart_code_version UNIQUE (code, version),
    CONSTRAINT ck_color_chart_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_color_chart_version CHECK (version >= 1),
    CONSTRAINT ck_color_chart_product_line
        CHECK (product_line IS NULL OR product_line IN ('MINI', 'STANDARD', 'PLUS')),
    CONSTRAINT ck_color_chart_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_color_chart_illuminant CHECK (illuminant IN ('D65')),
    CONSTRAINT ck_color_chart_observer CHECK (observer IN ('2')),
    CONSTRAINT ck_color_chart_delta_e_formula CHECK (delta_e_formula IN ('CIEDE2000')),
    CONSTRAINT ck_color_chart_source CHECK (source IN ('MANUAL_HEX', 'CALIBRATED', 'SPECTRO')),
    CONSTRAINT ck_color_chart_effective_range
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
    CONSTRAINT fk_color_chart_card_layout FOREIGN KEY (card_layout_id)
        REFERENCES reference_card_layout (id),
    CONSTRAINT fk_color_chart_created_by FOREIGN KEY (created_by)
        REFERENCES app_user (id)
);

-- SUA LOI CU PHAP (A6, xem docs/handovers/A6.md): CONSTRAINT ... UNIQUE(...) trong CREATE TABLE
-- KHONG chap nhan bieu thuc (chi ten cot tran) - "coalesce(production_batch, '')" lam Postgres
-- bao "syntax error at or near (" (xac nhan bang ApplicationContextSmokeTest that qua
-- Testcontainers that, khong phai suy doan). Rang buoc unique tren BIEU THUC phai la
-- CREATE UNIQUE INDEX, giu nguyen ngu nghia "it nhat mot bang ACTIVE cho moi (product_line,
-- production_batch) tai mot thoi diem" (p4 D5) - khong doi ten, khong doi cot.
CREATE UNIQUE INDEX IF NOT EXISTS uq_color_chart_active_per_batch
    ON color_chart (product_line, coalesce(production_batch, ''), status);

-- Tra bang mau hieu luc cho mot goi (p4 D5).
CREATE INDEX IF NOT EXISTS idx_color_chart_active_product_line
    ON color_chart (status, product_line, effective_from DESC) WHERE status = 'ACTIVE';

-- Admin thay ngay con bang nao chua hieu chuan (p4 D5).
CREATE INDEX IF NOT EXISTS idx_color_chart_placeholder
    ON color_chart (is_placeholder) WHERE is_placeholder;

COMMENT ON TABLE color_chart IS 'Bang mau pH (p4 D5). Khong bao gio sua ban ACTIVE - publish = tao version moi.';
COMMENT ON COLUMN color_chart.is_placeholder IS 'true cho seed ban dau. UI hien banner "dang hieu chuan", pipeline them flag CHART_PLACEHOLDER.';
COMMENT ON COLUMN color_chart.params IS 'JSONB: {kL:2, kC:1, kH:1, matchScaleDE:15, ...}.';

-- ===========================================================================
-- color_chart_point - cac muc pH cua bang mau (p4 D6).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS color_chart_point
(
    id                  UUID         NOT NULL,
    chart_id            UUID         NOT NULL,
    ph_value            NUMERIC(3,1) NOT NULL,
    sort_order          INT          NOT NULL,
    lab_l               NUMERIC(6,3) NOT NULL,
    lab_a               NUMERIC(6,3) NOT NULL,
    lab_b               NUMERIC(6,3) NOT NULL,
    tolerance_delta_e   NUMERIC(5,2) NOT NULL DEFAULT 4.0,
    -- VARCHAR(7) khong phai CHAR(7) (A6, xem docs/handovers/A6.md): entity JPA
    -- ColorChartPoint.hexSrgb/displayHex khai @Column(length=7) tren truong String, Hibernate
    -- suy ra VARCHAR khi validate schema; CHAR(7) lam ApplicationContextSmokeTest bao
    -- "wrong column type ... found bpchar, but expecting varchar(7)" (xac nhan bang Testcontainers
    -- that). Gia tri luon dung 7 ky tu ('#RRGGBB') nen VARCHAR(7) tuong duong CHAR(7) ve mat du
    -- lieu, chi khac o tang kiem tra cua Hibernate.
    hex_srgb            VARCHAR(7),
    display_hex         VARCHAR(7)   NOT NULL,
    display_name_vi     TEXT         NOT NULL,
    display_name_en     TEXT,
    sample_count        INT,
    sample_spread_de00  NUMERIC(5,2),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_color_chart_point PRIMARY KEY (id),
    CONSTRAINT uq_color_chart_point_ph UNIQUE (chart_id, ph_value),
    CONSTRAINT uq_color_chart_point_sort UNIQUE (chart_id, sort_order),
    CONSTRAINT ck_color_chart_point_ph CHECK (ph_value >= 0 AND ph_value <= 14),
    CONSTRAINT ck_color_chart_point_tolerance CHECK (tolerance_delta_e > 0),
    CONSTRAINT ck_color_chart_point_hex_srgb
        CHECK (hex_srgb IS NULL OR hex_srgb ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_color_chart_point_display_hex
        CHECK (display_hex ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_color_chart_point_sample_count
        CHECK (sample_count IS NULL OR sample_count >= 0),
    CONSTRAINT ck_color_chart_point_sample_spread
        CHECK (sample_spread_de00 IS NULL OR sample_spread_de00 >= 0),
    CONSTRAINT fk_color_chart_point_chart FOREIGN KEY (chart_id)
        REFERENCES color_chart (id) ON DELETE CASCADE
);

-- Doc ca da tuyen mot lan (bang nho, luon cache o tang ung dung).
CREATE INDEX IF NOT EXISTS idx_color_chart_point_chart_sort
    ON color_chart_point (chart_id, sort_order);

COMMENT ON TABLE color_chart_point IS 'Cac muc pH cua bang mau (p4 D6). lab_l/a/b la nguon su that cho matching.';
COMMENT ON COLUMN color_chart_point.display_hex IS 'Mau ve swatch tren UI - co the khac hex_srgb vi UI can mau de nhin.';

-- ===========================================================================
-- ph_classification_band - dai phan loai hien thi (p4 D7).
-- chart_id NULL = dai toan cuc (ap cho moi bang mau).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS ph_classification_band
(
    id              UUID         NOT NULL,
    chart_id        UUID,
    code            VARCHAR(24)  NOT NULL,
    min_ph          NUMERIC(3,1),
    max_ph          NUMERIC(3,1),
    min_inclusive   BOOLEAN      NOT NULL DEFAULT true,
    max_inclusive   BOOLEAN      NOT NULL DEFAULT true,
    label_vi        TEXT         NOT NULL,
    label_en        TEXT,
    description_vi  TEXT,
    description_en  TEXT,
    severity        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    color_token     VARCHAR(48)  NOT NULL,
    icon_name       VARCHAR(48)  NOT NULL,
    triggers_alert  BOOLEAN      NOT NULL DEFAULT false,
    sort_order      INT          NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_ph_classification_band PRIMARY KEY (id),
    CONSTRAINT ck_ph_classification_band_code
        CHECK (code IN ('IN_RANGE', 'SLIGHTLY_LOW', 'SLIGHTLY_HIGH', 'LOW', 'HIGH', 'INCONCLUSIVE')),
    CONSTRAINT ck_ph_classification_band_range
        CHECK (min_ph IS NULL OR max_ph IS NULL OR max_ph > min_ph),
    CONSTRAINT ck_ph_classification_band_severity
        CHECK (severity IN ('NORMAL', 'ATTENTION', 'WATCH', 'NEUTRAL')),
    CONSTRAINT fk_ph_classification_band_chart FOREIGN KEY (chart_id)
        REFERENCES color_chart (id) ON DELETE CASCADE
);

-- SUA LOI CU PHAP (A6, xem docs/handovers/A6.md): cung loai loi voi uq_color_chart_active_per_batch
-- o tren - CONSTRAINT UNIQUE(...) trong CREATE TABLE khong nhan bieu thuc coalesce(...).
CREATE UNIQUE INDEX IF NOT EXISTS uq_ph_classification_band_code
    ON ph_classification_band (coalesce(chart_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

-- Nap toan bo dai mot lan cho mot bang mau.
CREATE INDEX IF NOT EXISTS idx_ph_classification_band_chart_active_sort
    ON ph_classification_band (chart_id, active, sort_order);

COMMENT ON TABLE ph_classification_band IS 'Dai phan loai hien thi (p4 D7). Nhan la cau hinh, khong hard-code.';
COMMENT ON COLUMN ph_classification_band.color_token IS 'Ten token thiet ke (color-ph-*), KHONG phai hex.';

-- ===========================================================================
-- litter_substrate_profile - mau cat nen theo lo (p4 D9).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS litter_substrate_profile
(
    id               UUID         NOT NULL,
    product_line     VARCHAR(16)  NOT NULL,
    batch_code       VARCHAR(64),
    lab_l            NUMERIC(6,3) NOT NULL,
    lab_a            NUMERIC(6,3) NOT NULL,
    lab_b            NUMERIC(6,3) NOT NULL,
    chroma_max       NUMERIC(5,2) NOT NULL DEFAULT 8,
    target_coverage  NUMERIC(5,4) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_litter_substrate_profile PRIMARY KEY (id),
    CONSTRAINT ck_litter_substrate_profile_product_line
        CHECK (product_line IN ('MINI', 'STANDARD', 'PLUS')),
    CONSTRAINT ck_litter_substrate_profile_chroma_max CHECK (chroma_max > 0),
    CONSTRAINT ck_litter_substrate_profile_target_coverage
        CHECK (target_coverage > 0 AND target_coverage <= 1)
);

-- SUA LOI CU PHAP (A6, xem docs/handovers/A6.md): cung loai loi voi hai cho tren.
CREATE UNIQUE INDEX IF NOT EXISTS uq_litter_substrate_profile_product_batch
    ON litter_substrate_profile (product_line, coalesce(batch_code, ''));

COMMENT ON TABLE litter_substrate_profile IS 'Mau cat nen theo lo (p4 D9).';

-- ===========================================================================
-- FK vong giua color_chart <-> chart_calibration_job (p4 D5 calibration_job_id,
-- p4 D10 produced_chart_id). Ca hai deu nullable nen tao bang truoc, them FK sau.
-- ===========================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_color_chart_calibration_job'
    ) THEN
        ALTER TABLE color_chart
            ADD CONSTRAINT fk_color_chart_calibration_job
            FOREIGN KEY (calibration_job_id) REFERENCES chart_calibration_job (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_chart_calibration_job_produced_chart'
    ) THEN
        ALTER TABLE chart_calibration_job
            ADD CONSTRAINT fk_chart_calibration_job_produced_chart
            FOREIGN KEY (produced_chart_id) REFERENCES color_chart (id);
    END IF;
END
$$;

-- ===========================================================================
-- SEED: bang mau placeholder CHART-PLACEHOLDER-v1 (p6 §6.6.6).
--
-- 7 muc pH 5.5/6.0/6.3/6.6/7.0/7.5/8.0, mau du tren dai bromothymol-blue pho bien
-- (vang o pH thap -> xanh luc -> xanh lam o pH cao). Lab tinh bang ColorSpace.hexToLab
-- (sRGB EOTF -> XYZ D65 -> CIELAB). dE00 lien ke (kL=2): 9.83 / 8.66 / 10.70 / 19.44 /
-- 33.26 / 11.12 — tat ca >= 3 (rang buoc publish cua p6 §6.6.2).
--
-- ⚠ GIA TRI NAY CHI DE HE THONG CHAY DUOC END-TO-END TRONG DEV/STAGING.
-- TUYET DOI KHONG DUNG DE TUYEN BO DO CHINH XAC. Bang thuat la du lieu owner (X1),
-- hieu chuan la M7 — xem docs/handovers/A5.md.
-- ===========================================================================

-- Layout the tham chieu CC-CARD v1 (p4 D8, research-color-pipeline mục d).
INSERT INTO reference_card_layout (id, code, version, layout, active)
VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'CC-CARD',
    1,
    '{
        "code": "CC-CARD",
        "version": 1,
        "physical_mm": {"w": 85.6, "h": 54.0},
        "canonical_px": {"w": 600, "h": 380},
        "markers": {
            "type": "ARUCO_4X4_50",
            "ids": [0, 1, 2, 3],
            "qr": {"present": true, "payload_format": "CC|{cardVer}|{batchCode}"}
        },
        "patches": [
            {"id": "W1",  "role": "NEUTRAL", "rect": [0.06, 0.12, 0.14, 0.16], "ref_srgb": "#F2F2F2", "ref_lab": [95.494, 0.0, 0.0]},
            {"id": "G80", "role": "NEUTRAL", "rect": [0.22, 0.12, 0.14, 0.16], "ref_srgb": "#CCCCCC", "ref_lab": [82.046, 0.0, 0.0]},
            {"id": "G50", "role": "NEUTRAL", "rect": [0.38, 0.12, 0.14, 0.16], "ref_srgb": "#808080", "ref_lab": [53.585, 0.0, 0.0]},
            {"id": "G20", "role": "NEUTRAL", "rect": [0.54, 0.12, 0.14, 0.16], "ref_srgb": "#333333", "ref_lab": [21.247, 0.0, 0.0]},
            {"id": "BK",  "role": "NEUTRAL", "rect": [0.70, 0.12, 0.14, 0.16], "ref_srgb": "#111111", "ref_lab": [5.063, 0.0, 0.0]},
            {"id": "R",   "role": "COLOR",   "rect": [0.06, 0.34, 0.14, 0.16], "ref_srgb": "#C0392B", "ref_lab": [44.673, 52.908, 39.238]},
            {"id": "G",   "role": "COLOR",   "rect": [0.22, 0.34, 0.14, 0.16], "ref_srgb": "#27AE60", "ref_lab": [62.966, -52.798, 30.172]},
            {"id": "B",   "role": "COLOR",   "rect": [0.38, 0.34, 0.14, 0.16], "ref_srgb": "#2980B9", "ref_lab": [51.165, -5.657, -37.171]},
            {"id": "C",   "role": "COLOR",   "rect": [0.54, 0.34, 0.14, 0.16], "ref_srgb": "#16A085", "ref_lab": [58.978, -40.728, 4.411]},
            {"id": "M",   "role": "COLOR",   "rect": [0.70, 0.34, 0.14, 0.16], "ref_srgb": "#8E44AD", "ref_lab": [42.616, 48.480, -43.272]},
            {"id": "Y",   "role": "COLOR",   "rect": [0.86, 0.34, 0.14, 0.16], "ref_srgb": "#D4AC0D", "ref_lab": [71.915, 1.822, 73.128]},
            {"id": "PH_5_5", "role": "PH_LEVEL", "ph": 5.5, "rect": [0.06, 0.56, 0.14, 0.16], "ref_srgb": "#E8C832", "ref_lab": [81.124, -3.229, 72.989]},
            {"id": "PH_6_0", "role": "PH_LEVEL", "ph": 6.0, "rect": [0.22, 0.56, 0.14, 0.16], "ref_srgb": "#C9CE4F", "ref_lab": [80.250, -17.893, 60.596]},
            {"id": "PH_6_3", "role": "PH_LEVEL", "ph": 6.3, "rect": [0.38, 0.56, 0.14, 0.16], "ref_srgb": "#8FB83C", "ref_lab": [69.786, -32.070, 56.085]},
            {"id": "PH_6_6", "role": "PH_LEVEL", "ph": 6.6, "rect": [0.54, 0.56, 0.14, 0.16], "ref_srgb": "#4CAF50", "ref_lab": [63.978, -48.542, 39.735]},
            {"id": "PH_7_0", "role": "PH_LEVEL", "ph": 7.0, "rect": [0.70, 0.56, 0.14, 0.16], "ref_srgb": "#2E9E8F", "ref_lab": [58.990, -34.457, -1.182]},
            {"id": "PH_7_5", "role": "PH_LEVEL", "ph": 7.5, "rect": [0.86, 0.56, 0.14, 0.16], "ref_srgb": "#2E6FC9", "ref_lab": [47.191, 11.271, -52.665]},
            {"id": "PH_8_0", "role": "PH_LEVEL", "ph": 8.0, "rect": [0.06, 0.76, 0.14, 0.16], "ref_srgb": "#1F3FA8", "ref_lab": [31.051, 28.499, -59.481]}
        ]
    }'::jsonb,
    true
)
ON CONFLICT (id) DO NOTHING;

-- Bang mau placeholder.
INSERT INTO color_chart (id, code, version, name, product_line, production_batch,
                         card_layout_id, status, is_placeholder, illuminant, observer,
                         delta_e_formula, params, source, calibration_job_id,
                         effective_from, published_at, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000002'::uuid,
    'CHART-PLACEHOLDER-v1',
    1,
    'Bảng màu placeholder (chưa hiệu chuẩn)',
    'STANDARD',
    NULL,
    '00000000-0000-0000-0000-000000000001'::uuid,
    'ACTIVE',
    true,
    'D65',
    '2',
    'CIEDE2000',
    '{"kL":2,"kC":1,"kH":1,"matchScaleDE":15}'::jsonb,
    'MANUAL_HEX',
    NULL,
    now(),
    now(),
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- 7 muc pH. Lab = ColorSpace.hexToLab(hex) — xem comment o dau file.
INSERT INTO color_chart_point (id, chart_id, ph_value, sort_order, lab_l, lab_a, lab_b,
                                tolerance_delta_e, hex_srgb, display_hex,
                                display_name_vi, display_name_en, sample_count, sample_spread_de00)
VALUES
    ('00000000-0000-0000-0000-000000000010'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 5.5, 1, 81.124, -3.229, 72.989, 4.0, '#E8C832', '#E8C832', 'Vàng', 'Yellow', NULL, NULL),
    ('00000000-0000-0000-0000-000000000011'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 6.0, 2, 80.250, -17.893, 60.596, 4.0, '#C9CE4F', '#C9CE4F', 'Vàng lục', 'Yellow-green', NULL, NULL),
    ('00000000-0000-0000-0000-000000000012'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 6.3, 3, 69.786, -32.070, 56.085, 4.0, '#8FB83C', '#8FB83C', 'Xanh lục vàng', 'Green-yellow', NULL, NULL),
    ('00000000-0000-0000-0000-000000000013'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 6.6, 4, 63.978, -48.542, 39.735, 4.0, '#4CAF50', '#4CAF50', 'Xanh lục', 'Green', NULL, NULL),
    ('00000000-0000-0000-0000-000000000014'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 7.0, 5, 58.990, -34.457, -1.182, 4.0, '#2E9E8F', '#2E9E8F', 'Xanh ngọc', 'Teal', NULL, NULL),
    ('00000000-0000-0000-0000-000000000015'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 7.5, 6, 47.191, 11.271, -52.665, 4.0, '#2E6FC9', '#2E6FC9', 'Xanh lam', 'Blue', NULL, NULL),
    ('00000000-0000-0000-0000-000000000016'::uuid, '00000000-0000-0000-0000-000000000002'::uuid, 8.0, 7, 31.051, 28.499, -59.481, 4.0, '#1F3FA8', '#1F3FA8', 'Xanh lam đậm', 'Deep blue', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- ===========================================================================
-- SEED: 6 dai phan loai toan cuc (p4 §4.9.2 R__seed_ph_classification_band.sql,
-- p6 §6.7.1). chart_id = NULL (ap cho moi bang mau).
--
-- Phu kin truc pH, khong chong lap:
--   LOW           ph < 6.0
--   SLIGHTLY_LOW  6.0 <= ph < 6.3
--   IN_RANGE      6.3 <= ph <= 6.6
--   SLIGHTLY_HIGH 6.6 < ph <= 7.0
--   HIGH          ph > 7.0
--   INCONCLUSIVE   khong co khoang (khong bao gio khop theo pH)
-- ===========================================================================
INSERT INTO ph_classification_band (id, chart_id, code, min_ph, max_ph, min_inclusive, max_inclusive,
                                    label_vi, label_en, description_vi, description_en,
                                    severity, color_token, icon_name, triggers_alert, sort_order, active)
VALUES
    ('00000000-0000-0000-0000-000000000020'::uuid, NULL, 'LOW', NULL, 6.0, true, false,
     'Thấp rõ rệt (thiên axit)', 'Clearly low (acidic)',
     'Lệch khá nhiều so với khoảng tham chiếu. Bạn nên theo dõi thêm vài lần trong 1–2 ngày tới.',
     'Well outside the reference range. Consider tracking a few more times over the next 1–2 days.',
     'WATCH', 'color-ph-abnormal', 'alert-triangle', true, 1, true),
    ('00000000-0000-0000-0000-000000000021'::uuid, NULL, 'SLIGHTLY_LOW', 6.0, 6.3, true, false,
     'Hơi thấp (thiên axit)', 'Slightly low (acidic)',
     'Thấp hơn khoảng tham chiếu một chút. Thường không đáng lo nếu chỉ xảy ra một lần.',
     'Slightly below the reference range. Usually not a concern if it happens only once.',
     'ATTENTION', 'color-ph-mild', 'arrow-down-circle', true, 2, true),
    ('00000000-0000-0000-0000-000000000022'::uuid, NULL, 'IN_RANGE', 6.3, 6.6, true, true,
     'Trong khoảng tham chiếu', 'Within reference range',
     'pH {0} nằm trong khoảng tham chiếu 6,3–6,6.',
     'pH {0} is within the reference range 6.3–6.6.',
     'NORMAL', 'color-ph-normal', 'check-circle', false, 3, true),
    ('00000000-0000-0000-0000-000000000023'::uuid, NULL, 'SLIGHTLY_HIGH', 6.6, 7.0, false, true,
     'Hơi cao (thiên kiềm)', 'Slightly high (alkaline)',
     'Cao hơn khoảng tham chiếu một chút. Thường không đáng lo nếu chỉ xảy ra một lần.',
     'Slightly above the reference range. Usually not a concern if it happens only once.',
     'ATTENTION', 'color-ph-mild', 'arrow-up-circle', true, 4, true),
    ('00000000-0000-0000-0000-000000000024'::uuid, NULL, 'HIGH', 7.0, NULL, false, true,
     'Cao rõ rệt (thiên kiềm)', 'Clearly high (alkaline)',
     'Lệch khá nhiều so với khoảng tham chiếu. Bạn nên theo dõi thêm vài lần trong 1–2 ngày tới.',
     'Well outside the reference range. Consider tracking a few more times over the next 1–2 days.',
     'WATCH', 'color-ph-abnormal', 'alert-triangle', true, 5, true),
    ('00000000-0000-0000-0000-000000000025'::uuid, NULL, 'INCONCLUSIVE', NULL, NULL, true, true,
     'Chưa đủ dữ liệu để kết luận', 'Not enough data to conclude',
     'Ảnh chưa đủ điều kiện để đo màu chính xác. Hãy thử chụp lại theo hướng dẫn.',
     'The photo did not meet the conditions for accurate colour measurement. Please try again following the guide.',
     'NEUTRAL', 'color-ph-unknown', 'help-circle', false, 6, true)
ON CONFLICT (id) DO NOTHING;

-- ===========================================================================
-- SEED: ho so cat nen cho dong STANDARD (p4 D9). Mau nen clay xam be.
-- ===========================================================================
INSERT INTO litter_substrate_profile (id, product_line, batch_code, lab_l, lab_a, lab_b,
                                     chroma_max, target_coverage, active)
VALUES (
    '00000000-0000-0000-0000-000000000030'::uuid,
    'STANDARD',
    NULL,
    66.814,
    3.154,
    10.461,
    8.0,
    0.015,
    true
)
ON CONFLICT (id) DO NOTHING;
