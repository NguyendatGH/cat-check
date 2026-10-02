-- V8: Ho so meo & suc khoe nen - module `cat` (p7 §7.2.3).
--
-- Nguon dac ta: spec/parts/p4-domain-model-erd.md §4.9.2 (dong V8) - "cat, cat_health_survey,
-- cat_note, cat_clinical_sign_report (cot scan_id them o V11)", phu thuoc V5, V7.
--
-- ⚠ HAI COT `scan_id` CO TINH KHONG DUOC TAO O DAY:
--   · `cat_note.scan_id`             -> them o V11 (module `scan`)
--   · `cat_clinical_sign_report.scan_id` -> them o V11 (module `scan`)
-- Bang `scan` chua ton tai o V8 nen khong the tao FK. Day la chu dich cua p4 §4.9.2, khong phai
-- so huot. Xem docs/handovers/A3.md muc "V11 can bo sung gi".
--
-- ⚠ `cat_clinical_sign_report.health_flag_id` TAO COT O DAY NHUNG CHUA TAO FK:
-- `health_flag` duoc tao o V12 (module `insight`), sau V8. Theo tinh than p4 §4.9.3 ("ba vong
-- lap phai xu ly tuong minh"), FK `health_flag_id -> health_flag(id) ON DELETE SET NULL` se
-- duoc V12 bo sung. Xem docs/handovers/A3.md - can A6/W3 bo sung khi tao V12.
--
-- ⚠ Moi cot thoi gian la TIMESTAMPTZ, cot ngay la DATE (p4 §4.1.2). Moi cot id la UUID
-- DEFAULT uuidv7() (p4 §4.1.1). Moi enum luu bang VARCHAR + CHECK, KHONG dung ENUM (p4 §4.1.3).
-- KHONG tao trigger `set_updated_at` o day - p4 §4.9.2 giao V16 cho W3.
--
-- ⚠ KHONG tao bang `cat_baseline` (p4 §4.3 K5 cam ro) va KHONG co bang `stored_file` nao
-- (khoa luu tru anh nam tren chinh `cat.avatar_storage_key` / `avatar_storage_provider`).

-- ===========================================================================
-- C1. cat - ho so meo
-- ===========================================================================
CREATE TABLE IF NOT EXISTS cat
(
    id                      UUID          NOT NULL DEFAULT uuidv7(),
    owner_id                UUID          NOT NULL,
    name                    TEXT          NOT NULL,
    birth_date              DATE,
    approx_age_months       INT,
    breed_code              VARCHAR(48),
    breed_other             TEXT,
    coat_color              VARCHAR(48),
    sex                     VARCHAR(8)    NOT NULL DEFAULT 'UNKNOWN',
    neutered                BOOLEAN,
    weight_kg               NUMERIC(4, 2),
    weight_updated_at       TIMESTAMPTZ,
    avatar_storage_key      TEXT,
    avatar_storage_provider VARCHAR(16),
    public_code             VARCHAR(24)   NOT NULL,
    status                  VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    is_primary              BOOLEAN       NOT NULL DEFAULT false,
    notes                   TEXT,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_cat PRIMARY KEY (id),
    CONSTRAINT ck_cat_name CHECK (length(btrim(name)) BETWEEN 1 AND 60),
    -- p4 §4.1.2: cot chi la ngay dung DATE, va khong duoc o tuong lai.
    CONSTRAINT ck_cat_birth_date CHECK (birth_date IS NULL OR birth_date <= CURRENT_DATE),
    -- p4 C1 ghi `CHECK (birth_date IS NOT NULL OR approx_age_months IS NOT NULL OR
    -- (birth_date IS NULL AND approx_age_months IS NULL))` - ve bi dang ta nen luon dung.
    -- Y nghia that su lay tu p8 §8.5.3: gui ca hai thi `400 CAT_AGE_CONFLICT`, va p8 §8.5.3
    -- tinh `ageMonths` tu MOT trong hai truong => dung chinh xac MOT truong.
    CONSTRAINT ck_cat_age_source CHECK (birth_date IS NOT NULL OR approx_age_months IS NOT NULL),
    CONSTRAINT ck_cat_age_not_conflict CHECK (NOT (birth_date IS NOT NULL AND approx_age_months IS NOT NULL)),
    CONSTRAINT ck_cat_approx_age_months CHECK (approx_age_months IS NULL OR approx_age_months BETWEEN 0 AND 360),
    CONSTRAINT ck_cat_sex CHECK (sex IN ('MALE', 'FEMALE', 'UNKNOWN')),
    CONSTRAINT ck_cat_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    -- p4 C1 ghi `CHECK (weight_kg > 0 AND weight_kg < 30)`. p8 §8.2.4 vi du in "0,1 - 25 kg".
    -- p4 la so huu ten cot/ranh buoc (spec/04-index.md §2) nen dung 0 < weight_kg < 30.
    CONSTRAINT ck_cat_weight_kg CHECK (weight_kg IS NULL OR (weight_kg > 0 AND weight_kg < 30)),
    CONSTRAINT ck_cat_avatar_storage_provider CHECK (avatar_storage_provider IS NULL
        OR avatar_storage_provider IN ('LOCAL', 'CLOUDINARY')),
    -- p4 C1: `CC-VN-<6 ky tu Crockford Base32>` - bo ky tu I, L, O, U.
    CONSTRAINT ck_cat_public_code CHECK (public_code ~ '^CC-VN-[0-9A-HJKMNP-TV-Z]{6}$')
);

-- p4 C1 danh sach index.
CREATE INDEX IF NOT EXISTS idx_cat_owner_status
    ON cat (owner_id, status)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cat_public_code
    ON cat (public_code);
-- p4 C1 / I23: toi da 1 meo chinh / owner (partial unique index).
CREATE UNIQUE INDEX IF NOT EXISTS uq_cat_owner_primary
    ON cat (owner_id)
    WHERE is_primary AND deleted_at IS NULL;
-- Phuc vu dem so ho so de kiem tra I27 (p8 §8.5.3 thu tu kiem han muc).
CREATE INDEX IF NOT EXISTS idx_cat_owner_alive
    ON cat (owner_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE cat IS 'Ho so meo (module `cat`, p4 C1). Soft delete bang deleted_at - xoa cung se lam scan.cat_id va PDF da xuat khong con hieu luc.';
COMMENT ON COLUMN cat.owner_id IS 'FK -> app_user(id) (module `identity`). COT UUID thuan - KHONG dung JPA association (R6 + ORCHESTRATOR §5).';
COMMENT ON COLUMN cat.public_code IS 'Ma dep hien thi cho nguoi dung, CC-VN-<6 Crockford Base32>. KHONG dung ten meo trong ma (ten doi duoc).';
COMMENT ON COLUMN cat.avatar_storage_key IS 'Khoa luu anh trong storage, KHONG luu URL (research-integrations §4.1). Module `media` khong so huu bang nay.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_owner')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE cat
            ADD CONSTRAINT fk_cat_owner
                FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_breed')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'cat_breed') THEN
        ALTER TABLE cat
            ADD CONSTRAINT fk_cat_breed
                FOREIGN KEY (breed_code) REFERENCES cat_breed (code);
    END IF;
END
$$;

-- ===========================================================================
-- C3. cat_health_survey - khao sat suc khoe ban dau. NHIEU dong tren mot meo, khong phai 1-1
-- (p4 C3: ban moi nhat la ban co hieu luc; giu lich su de PDF noi "tai thoi diem do chu khai
-- dang cho an gi").
-- ===========================================================================
CREATE TABLE IF NOT EXISTS cat_health_survey
(
    id                    UUID        NOT NULL DEFAULT uuidv7(),
    cat_id                UUID        NOT NULL,
    questionnaire_version VARCHAR(16) NOT NULL,
    -- p4 §4.1.5: JSONB DUNG o day - bo cau hoi co version, cau hoi se them/bot;
    -- khong truy van nao loc theo mot cau tra loi cu the o Phase 1.
    answers               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    skipped               BOOLEAN     NOT NULL DEFAULT false,
    submitted_at          TIMESTAMPTZ,
    submitted_by          UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_cat_health_survey PRIMARY KEY (id),
    -- p4 C3: "skipped = true" thi submitted_at = NULL; nguoc lai phai co submitted_at.
    CONSTRAINT ck_cat_health_survey_submitted CHECK (skipped OR submitted_at IS NOT NULL),
    CONSTRAINT ck_cat_health_survey_skipped CHECK (NOT skipped OR submitted_at IS NULL)
);

-- p4 C3: "(cat_id, created_at DESC)" - lay ban khao sat moi nhat cua meo.
CREATE INDEX IF NOT EXISTS idx_cat_health_survey_cat_created
    ON cat_health_survey (cat_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cat_health_survey_submitted_by
    ON cat_health_survey (submitted_by);

COMMENT ON TABLE cat_health_survey IS 'Khao sat suc khoe ban dau (p4 C3). NHIEU dong/meo; ban moi nhat la ban co hieu luc.';
COMMENT ON COLUMN cat_health_survey.answers IS 'JSONB - validate bang JSON Schema theo questionnaire_version, KHONG bang CHECK (p4 §4.4.4).';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_health_survey_cat')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'cat') THEN
        ALTER TABLE cat_health_survey
            ADD CONSTRAINT fk_cat_health_survey_cat
                FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_health_survey_submitted_by')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE cat_health_survey
            ADD CONSTRAINT fk_cat_health_survey_submitted_by
                FOREIGN KEY (submitted_by) REFERENCES app_user (id);
    END IF;
END
$$;

-- ===========================================================================
-- C4. cat_note - ghi chu cua chu nuoi. `note_type = SYMPTOM` KHONG duoc dung de suy luan
-- benh (p4 C4) - chi la nhan de loc va dua vao PDF cho bac si doc.
-- KHONG co cot `scan_id` o day (them o V11).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS cat_note
(
    id            UUID        NOT NULL DEFAULT uuidv7(),
    cat_id        UUID        NOT NULL,
    author_user_id UUID       NOT NULL,
    note_type     VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
    body          TEXT        NOT NULL,
    occurred_on   DATE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_cat_note PRIMARY KEY (id),
    CONSTRAINT ck_cat_note_body CHECK (length(btrim(body)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_cat_note_type CHECK (note_type IN
        ('GENERAL', 'DIET_CHANGE', 'SYMPTOM', 'VET_VISIT', 'LITTER_CHANGE')),
    CONSTRAINT ck_cat_note_occurred_on CHECK (occurred_on IS NULL OR occurred_on <= CURRENT_DATE)
);

-- p4 C4 danh sach index.
CREATE INDEX IF NOT EXISTS idx_cat_note_timeline
    ON cat_note (cat_id, occurred_on DESC NULLS LAST, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_cat_note_author
    ON cat_note (author_user_id);

COMMENT ON TABLE cat_note IS 'Ghi chu cua chu nuoi (p4 C4). Soft delete. KHONG dung de suy luan benh.';
COMMENT ON COLUMN cat_note.author_user_id IS 'FK -> app_user(id). p8 CAT_NOTE_NOT_EDITABLE: chi nguoi viet moi sua duoc ghi chu nay.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_note_cat')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'cat') THEN
        ALTER TABLE cat_note
            ADD CONSTRAINT fk_cat_note_cat
                FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_note_author')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE cat_note
            ADD CONSTRAINT fk_cat_note_author
                FOREIGN KEY (author_user_id) REFERENCES app_user (id);
    END IF;
END
$$;

-- ===========================================================================
-- C5. cat_clinical_sign_report - dau hieu lam sang do chu nuoi khai.
-- KHONG dung mang noi: tap 6 gia tri co dinh, doc nguyen khoi, bo bang noi (p4 C5).
-- KHONG co cot `scan_id` o day (them o V11). Cot `health_flag_id` co, FK them o V12.
-- ===========================================================================
CREATE TABLE IF NOT EXISTS cat_clinical_sign_report
(
    id              UUID          NOT NULL DEFAULT uuidv7(),
    cat_id          UUID          NOT NULL,
    reported_by     UUID          NOT NULL,
    signs           VARCHAR(32)[] NOT NULL,
    source          VARCHAR(24)   NOT NULL,
    blocking_shown  BOOLEAN       NOT NULL DEFAULT false,
    reported_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    -- Cot ton tai; FK -> health_flag(id) se duoc V12 bo sung (health_flag sinh o V12).
    health_flag_id  UUID,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_cat_clinical_sign_report PRIMARY KEY (id),
    -- array_length tra ve NULL cho mang rong, ma `NULL >= 1` la NULL (khong phai FALSE) nen CHECK
    -- se pass. Phai coalesce ve 0 moi chan duoc dap rong.
    CONSTRAINT ck_cat_clinical_sign_report_signs_count CHECK (coalesce(array_length(signs, 1), 0) >= 1),
    -- p4 C5: "<@ " kiem tra tap con - thay the bo bang noi.
    CONSTRAINT ck_cat_clinical_sign_report_signs_values CHECK (signs <@ ARRAY[
        'STRAINING', 'NO_URINE', 'CRYING', 'BLOOD_VISIBLE',
        'LETHARGY_ANOREXIA', 'EXCESSIVE_LICKING']::VARCHAR(32)[]),
    CONSTRAINT ck_cat_clinical_sign_report_source CHECK (source IN
        ('RESULT_SCREEN', 'SURVEY', 'MANUAL'))
);

-- p4 C5 danh sach index.
CREATE INDEX IF NOT EXISTS idx_cat_clinical_sign_report_cat_reported
    ON cat_clinical_sign_report (cat_id, reported_at DESC);
CREATE INDEX IF NOT EXISTS idx_cat_clinical_sign_report_unacknowledged
    ON cat_clinical_sign_report (cat_id)
    WHERE acknowledged_at IS NULL;

COMMENT ON TABLE cat_clinical_sign_report IS 'Dau hieu lam sang do chu nuoi khai (p4 C5). KHONG suy doan benh, KHONG tinh diem nguy co, KHONG hien ten benh (quyet dinh #6, #8).';
COMMENT ON COLUMN cat_clinical_sign_report.signs IS 'Tap con cua 6 gia tri, CHECK bang toan tu <@. Mang VARCHAR(32)[] + CHECK, KHONG dung bang noi.';
COMMENT ON COLUMN cat_clinical_sign_report.health_flag_id IS 'FK se bo sung o V12 - xem dau file va docs/handovers/A3.md.';
COMMENT ON COLUMN cat_clinical_sign_report.blocking_shown IS 'Da hien modal chan man hay chi banner - p6 §6.9.6 quy dinh theo to hop dau hieu.';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_clinical_sign_report_cat')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'cat') THEN
        ALTER TABLE cat_clinical_sign_report
            ADD CONSTRAINT fk_cat_clinical_sign_report_cat
                FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cat_clinical_sign_report_reported_by')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE cat_clinical_sign_report
            ADD CONSTRAINT fk_cat_clinical_sign_report_reported_by
                FOREIGN KEY (reported_by) REFERENCES app_user (id);
    END IF;
END
$$;
