-- V7: Catalog - danh muc tinh thong dung chung cho ca he thong.
--
-- Nguon dac ta: spec/parts/p4-domain-model-erd.md §4.9.2 (dong V7) - "cat_breed,
-- package_plan, app_setting, care_tip", phu thuoc V5.
--
-- ⚠ LUU Y VE PHAN BO SO HUUU (su khac biet co chu dich, xem docs/handovers/A3.md):
-- File nay tao 4 bang thuoc 3 module khac nhau, dung theo danh muc §4.9.2 cua p4:
--   · cat_breed      -> module `cat`      (A3)
--   · care_tip       -> module `content`  (A3)
--   · package_plan   -> module `credit`   (A4)  <- CHI tao schema, KHONG tao entity
--   · app_setting    -> module `shared`   (W3) <- CHI tao schema, KHONG tao entity
-- Module `cat` KHONG dung entity cho `package_plan`/`app_setting` (R6 + p7 §7.2.3: moi bang
-- thuoc dung mot module); module `cat` doc chung qua port rieng cua no.
--
-- ⚠ LUU Y VE SEED DATA: p4 §4.9.1 noi "chi schema, du lieu seed di bang R__ repeatable".
-- File `R__` bi test `MigrationNamingTests#allMigrationFilesMatchNamingConvention` (ArchUnit
-- R17, regex ^V\d+(\.\d+)?__[a-z0-9_]+\.sql$) loai, va `spring.flyway.locations` chi tro
-- `classpath:db/migration`. Do do seed danh muc (cat_breed, care_tip) nam trong chinh V7.
-- Seed cua `app_setting`/`package_plan` KHONG nam o day - thuoc file R__ cua W3 va A4.
--
-- Moi bang deu KHONG co trigger `set_updated_at`: p4 §4.9.2 giao V16
-- ("trigger set_updated_at cho moi bang co updated_at") cho W3.
--
-- Moi cot thoi gian la TIMESTAMPTZ (p4 §4.1.2). Moi cot ID la UUID native 16 byte,
-- DEFAULT uuidv7() vi PostgreSQL 18.6 da co ham native (p4 §4.1.1 muc 2).
-- Moi enum luu bang VARCHAR + CHECK, KHONG dung PostgreSQL ENUM (p4 §4.1.3).

-- ===========================================================================
-- cat_breed - danh muc giong meo. Khoa tu nhien = code VARCHAR(48) (p4 §4.1.1 bang ngoai
-- lec "bang cau hinh dung khoa tu nhien"), p7 §7.2.3: `cat_breed` thuoc module `cat`.
-- ===========================================================================
CREATE TABLE IF NOT EXISTS cat_breed
(
    code       VARCHAR(48) NOT NULL,
    name_vi    TEXT        NOT NULL,
    name_en    TEXT        NOT NULL,
    popular    BOOLEAN     NOT NULL DEFAULT false,
    sort_order INT         NOT NULL DEFAULT 100,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_cat_breed PRIMARY KEY (code),
    CONSTRAINT ck_cat_breed_name_vi CHECK (length(btrim(name_vi)) > 0),
    CONSTRAINT ck_cat_breed_name_en CHECK (length(btrim(name_en)) > 0)
);

-- p4 C2: index (active, popular, sort_order) - render dropdown/chip.
CREATE INDEX IF NOT EXISTS idx_cat_breed_active_popular_sort_order
    ON cat_breed (active, popular, sort_order);

COMMENT ON TABLE cat_breed IS 'Danh muc giong meo (module `cat`, p4 C2). Khoa tu nhien = code.';
COMMENT ON COLUMN cat_breed.popular IS 'Hien o chip goi y nhanh (man M1 01c-3).';

-- ===========================================================================
-- package_plan - cau hinh 5 goi san pham. Khoa tu nhien = code VARCHAR(32) (p4 §4.1.1).
-- Cot theo p5 §5.3 (module `credit` se so huu entity cho bang nay; A3 chi tao schema).
-- p4 nhom E: them `updated_at` vao `package_plan` (mac dinh p5 chi ghi `created_at`).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS package_plan
(
    code                  VARCHAR(32)  NOT NULL,
    name                  VARCHAR(64)  NOT NULL,
    weight_kg             NUMERIC(4, 2) NOT NULL,
    credit_amount         INT          NOT NULL,
    credit_validity_days  INT          NOT NULL,
    max_cat_profiles      INT,
    features              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active                BOOLEAN      NOT NULL DEFAULT true,
    version               INT          NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_package_plan PRIMARY KEY (code),
    CONSTRAINT ck_package_plan_weight CHECK (weight_kg > 0),
    CONSTRAINT ck_package_plan_credit_amount CHECK (credit_amount > 0),
    CONSTRAINT ck_package_plan_validity CHECK (credit_validity_days > 0),
    -- NULL = khong gioi han (p5 §5.3) - p4 I27 doc gia tri NULL nhu "khong gioi han".
    CONSTRAINT ck_package_plan_max_cat_profiles CHECK (max_cat_profiles IS NULL OR max_cat_profiles > 0),
    CONSTRAINT ck_package_plan_version CHECK (version > 0)
);

COMMENT ON TABLE package_plan IS 'Cau hinh goi san pham (module `credit`, p5 §5.3). KHONG xoa bao gio, chi active=false.';
COMMENT ON COLUMN package_plan.max_cat_profiles IS 'NULL = khong gioi han. Chi ap dung khi TAO MOI, khong hoi to (p5 R5).';

-- ===========================================================================
-- app_setting - key/value store cau hinh runtime. Khoa tu nhien = key VARCHAR(128).
-- p7 §7.2.3: `app_setting` thuoc module `shared` (doc o moi module + duong nong) - A3 chi tao
-- schema, W3 se them entity + adapter. Module `cat` doc qua `cat.application.spi.AppSettingPort`
-- cua no (khong query thang bang cua module khac).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS app_setting
(
    key         VARCHAR(128) NOT NULL,
    value       JSONB        NOT NULL,
    value_type  VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    description TEXT,
    secret      BOOLEAN      NOT NULL DEFAULT false,
    updated_by  UUID,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_setting PRIMARY KEY (key),
    -- p4 H3: regex rang buoc namespace - bat buoc co dau cham.
    CONSTRAINT ck_app_setting_key_format CHECK (key ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'),
    CONSTRAINT ck_app_setting_value_type CHECK (value_type IN ('STRING', 'INT', 'BOOL', 'JSON'))
);

CREATE INDEX IF NOT EXISTS idx_app_setting_updated_by
    ON app_setting (updated_by);

COMMENT ON TABLE app_setting IS 'Cau hinh runtime (module `shared`, p4 H3). KHONG luu bi mat that - bi mat that thuoc bien moi truong (p18).';
COMMENT ON COLUMN app_setting.secret IS 'true = che gia tri trong UI va audit. Chi dung cho gia tri nhay cam muc thap.';

-- FK sang app_user: V7 phu thuoc V5 (p4 §4.9.2) nen tao duoc ngay. Guard kiem tra ca bang va
-- ca constraint de chay lai V7 khong loi.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_app_setting_updated_by')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE app_setting
            ADD CONSTRAINT fk_app_setting_updated_by
                FOREIGN KEY (updated_by) REFERENCES app_user (id) ON DELETE SET NULL;
    END IF;
END
$$;

-- ===========================================================================
-- care_tip - noi dung kien thuc cham soc. MOT BANG duy nhat (p4 §4.1.7 cam
-- `content_article`/`content_category`; `kind` va `category` la COT, khong phai bang).
-- Module so huu: `content` (p7 §7.2.3).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS care_tip
(
    id                 UUID         NOT NULL DEFAULT uuidv7(),
    slug               VARCHAR(120) NOT NULL,
    translation_group  UUID         NOT NULL DEFAULT uuidv7(),
    locale             VARCHAR(8)   NOT NULL DEFAULT 'vi',
    kind               VARCHAR(12)  NOT NULL DEFAULT 'TIP',
    category           VARCHAR(40),
    title              TEXT         NOT NULL,
    summary            TEXT,
    body_md            TEXT,
    cover_image_url    TEXT,
    tags               JSONB        NOT NULL DEFAULT '[]'::jsonb,
    status             VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    claim_type         VARCHAR(24)  NOT NULL,
    source_reference   TEXT,
    sort_weight        INT          NOT NULL DEFAULT 100,
    author_id          UUID,
    reviewed_by        UUID,
    reviewed_at        TIMESTAMPTZ,
    review_note        TEXT,
    published_at       TIMESTAMPTZ,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_care_tip PRIMARY KEY (id),
    -- p4 H1: "UNIQUE (slug, locale)" - dinh tuyen /tips/{slug} theo ngon ngữ.
    CONSTRAINT uq_care_tip_slug_locale UNIQUE (slug, locale),
    CONSTRAINT ck_care_tip_locale CHECK (locale IN ('vi', 'en')),
    CONSTRAINT ck_care_tip_kind CHECK (kind IN ('TIP', 'ARTICLE', 'FAQ')),
    CONSTRAINT ck_care_tip_category CHECK (category IS NULL OR category IN
        ('HYDRATION', 'LITTER', 'SCAN_HOWTO', 'DIET', 'GENERAL')),
    CONSTRAINT ck_care_tip_title CHECK (length(btrim(title)) BETWEEN 1 AND 200),
    -- p4 H1: "body_md - bat buoc voi ARTICLE".
    CONSTRAINT ck_care_tip_article_body CHECK (kind <> 'ARTICLE' OR body_md IS NOT NULL),
    -- p4 §4.4.8: 4 gia tri, them IN_REVIEW theo p14 §14.3.5(c).
    CONSTRAINT ck_care_tip_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED')),
    -- p4 §4.4.8: KHONG co DEFAULT - nguoi soan buoc phai chon, mac dinh se duoc chon 100%.
    CONSTRAINT ck_care_tip_claim_type CHECK (claim_type IN
        ('NONE', 'MEDICAL', 'STATISTIC', 'CERTIFICATION', 'PERFORMANCE', 'SERVICE_COMMITMENT')),
    -- p4 H1: chi ep do dai toi thieu, KHONG validate bang regex.
    CONSTRAINT ck_care_tip_source_reference CHECK (source_reference IS NULL
        OR length(btrim(source_reference)) <= 2000)
);

-- p4 H1 danh sach index.
CREATE INDEX IF NOT EXISTS idx_care_tip_listing
    ON care_tip (locale, status, kind, sort_weight)
    WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_care_tip_review_queue
    ON care_tip (status, updated_at DESC)
    WHERE status = 'IN_REVIEW';
CREATE INDEX IF NOT EXISTS idx_care_tip_published_claims
    ON care_tip (claim_type)
    WHERE claim_type <> 'NONE' AND status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_care_tip_translation_group_locale
    ON care_tip (translation_group, locale);
CREATE INDEX IF NOT EXISTS idx_care_tip_tags
    ON care_tip USING GIN (tags jsonb_path_ops);

COMMENT ON TABLE care_tip IS 'Noi dung kien thuc cham soc (module `content`, p4 H1). MOT bang cho ca TIP/ARTICLE/FAQ.';
COMMENT ON COLUMN care_tip.claim_type IS 'Khong co DEFAULT (p4 §4.4.8). Moi gia tri khac NONE deu bat buoc source_reference (bat bien I31 - khai bao CHECK o V16 theo p4 §4.9.2).';
COMMENT ON COLUMN care_tip.status IS 'DRAFT | IN_REVIEW | PUBLISHED | ARCHIVED. Chuyen trang thai deu ghi audit_log (CONTENT.*).';

-- FK sang app_user (V7 phu thuoc V5 nen tao duoc ngay).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_care_tip_author')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE care_tip
            ADD CONSTRAINT fk_care_tip_author
                FOREIGN KEY (author_id) REFERENCES app_user (id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_care_tip_reviewed_by')
       AND EXISTS (SELECT 1 FROM pg_class WHERE relname = 'app_user') THEN
        ALTER TABLE care_tip
            ADD CONSTRAINT fk_care_tip_reviewed_by
                FOREIGN KEY (reviewed_by) REFERENCES app_user (id) ON DELETE SET NULL;
    END IF;
END
$$;

-- ----------------------------------------------------------------------------
-- SEED: cat_breed — ~20 giong pho bien tai VN + OTHER (p4 §4.9.2 R__seed_cat_breed.sql).
-- Nguon danh muc mo: p4 §4.4.4 ("Danh muc mo: DOMESTIC_SHORTHAIR, BRITISH_SHORTHAIR,
-- PERSIAN, RAGDOLL, MAINE_COON, SIAMESE, SCOTTISH_FOLD, MUNCHKIN, BENGAL, SPHYNX, OTHER").
-- 4 giong `popular = true` la 4 chip o man M1 01c-3 (p4 C2).
-- Idempotent: ON CONFLICT (code) DO UPDATE.
-- ----------------------------------------------------------------------------
INSERT INTO cat_breed (code, name_vi, name_en, popular, sort_order) VALUES
    ('DOMESTIC_SHORTHAIR', 'Mèo lông ngắn Việt Nam', 'Domestic Shorthair',         true,   10),
    ('BRITISH_SHORTHAIR',  'Mèo Anh lông ngắn',     'British Shorthair',          true,   20),
    ('RAGDOLL',            'Mèo Ragdoll',             'Ragdoll',                    true,   30),
    ('PERSIAN',            'Mèo Ba Tư',              'Persian',                    true,   40),
    ('MAINE_COON',         'Mèo Maine Coon',         'Maine Coon',                 false,  50),
    ('SIAMESE',            'Mèo Xiêm',               'Siamese',                    false,  60),
    ('SCOTTISH_FOLD',      'Mèo Scottish Fold',      'Scottish Fold',              false,  70),
    ('MUNCHKIN',           'Mèo Munchkin',            'Munchkin',                   false,  80),
    ('BENGAL',             'Mèo Bengal',             'Bengal',                     false,  90),
    ('SPHYNX',             'Mèo Sphynx',             'Sphynx',                     false, 100),
    ('SIAMESE_MIX',        'Mèo lai Xiêm',           'Siamese mix',                false, 110),
    ('PERSIAN_MIX',        'Mèo lai Ba Tư',          'Persian mix',                false, 120),
    ('BRITISH_SHORTHAIR_MIX', 'Mèo lai Anh lông ngắn', 'British Shorthair mix',   false, 130),
    ('EXOTIC_SHORTHAIR',   'Mèo lông ngắn Exotic',  'Exotic Shorthair',           false, 140),
    ('DEVON_REX',          'Mèo Devon Rex',          'Devon Rex',                  false, 150),
    ('KORAT',              'Mèo Korat',              'Korat',                      false, 160),
    ('BIRMAN',             'Mèo Birman',             'Birman',                     false, 170),
    ('TURKISH_ANGORA',     'Mèo Ankara Thổ Nhĩ Kỳ',  'Turkish Angora',             false, 180),
    ('MIXED_UNKNOWN',      'Mèo lai / không rõ',    'Mixed / unknown',            false, 190),
    ('OTHER',              'Khác',                   'Other',                      false, 999)
ON CONFLICT (code) DO UPDATE
    SET name_vi   = EXCLUDED.name_vi,
        name_en   = EXCLUDED.name_en,
        popular   = EXCLUDED.popular,
        sort_order = EXCLUDED.sort_order,
        updated_at = now();

-- ----------------------------------------------------------------------------
-- SEED: care_tip — NOI DUNG PLACEHOLDER, CHUA DUOC KIEM CHUNG.
--
-- ⚠ p4 H1: "cac claim y khoa chua kiem chung ... KHONG duoc seed vao bang nay".
-- ⚠ ORCHESTRATOR §4 luat do 2: khong hua hen y te trong copy.
-- ⇒ moi dong seed deu `claim_type = 'NONE'` (huong dan thao tac, khong neu su that y khoa
--   hay so lieu nao — dinh nghia p4 §4.4.8) va `source_reference` NULL.
-- ⚠ Day la placeholder de UI co noi dung de hien thi; owner can soan lai noi dung that
--   va dua qua review 2 nguoi (p14 §14.3.5) truoc khi go-live. Xem docs/handovers/A3.md.
-- Chi seed tieng Viet (locale='vi'). Ban tieng Anh do bien dich soan sau.
-- ----------------------------------------------------------------------------
INSERT INTO care_tip (id, slug, translation_group, locale, kind, category, title, summary, body_md, tags, status, claim_type, sort_weight, published_at)
VALUES
    (uuidv7(), 'phuong-phap-chup-anh-cat',          uuidv7(), 'vi', 'TIP',     'SCAN_HOWTO',
     'Cách chụp ảnh khay cát cho dễ đọc màu',
     'Bốn bước chuẩn bị trước khi bấm chụp trong app.',
     E'1. Dọn khay cát, chỉ để lớp cát mỏng đều.\n2. Đặt thẻ màu tham chiếu cạnh khay, nằm trong khung hình.\n3. Áp sáng đều, tránh bóng tố và đèn trực diện.\n4. Giữ máy ổn định, chụp cận cảnh vùng cát.',
     '["scan","howto"]'::jsonb, 'PUBLISHED', 'NONE', 10, now()),
    (uuidv7(), 'doc-mau-sau-khi-quet',              uuidv7(), 'vi', 'FAQ',     'SCAN_HOWTO',
     'Ảnh mẫu của tôi được giữ bao lâu?',
     'Chính sách lưu trữ hiện tại của CatCheck.',
     E'Ảnh gốc của lần quét được giữ trong thời hạn lưu trữ do CatCheck công bố. Kết quả phân tích vẫn được giữ để bạn xem lại lịch sử.\n\nBạn có thể xoá ảnh ngay khi muốn trong phần cài đặt.',
     '["scan","retention"]'::jsonb, 'PUBLISHED', 'NONE', 20, now()),
    (uuidv7(), 've-sinh-khay-cat-hang-ngay',         uuidv7(), 'vi', 'TIP',     'LITTER',
     'Vệ sinh khay cát hằng ngày',
     'Một vài thói quen giúp khay cát luôn khô và thoáng.',
     E'- Dọn cát bẩn hằng ngày.\n- Thay toàn bộ lớp cát theo chu kỳ phù hợp với loại cát bạn dùng.\n- Rửa khay và phơi khô trước khi đổ cát mới.',
     '["litter","daily"]'::jsonb, 'PUBLISHED', 'NONE', 30, now()),
    (uuidv7(), 'cat-check-la-gi',                    uuidv7(), 'vi', 'ARTICLE', 'GENERAL',
     'CatCheck là gì và CatCheck không phải là gì',
     'Giới thiệu ngắn về công cụ và giới hạn của nó.',
     E'# CatCheck là gì\n\nCatCheck giúp bạn ghi lại màu cát vệ sinh theo thời gian và xem lại xu hướng của mèo mình.\n\n# CatCheck không phải là gì\n\nCatCheck **không phải thiết bị y tế** và **không chẩn đoán bệnh**. Kết quả chỉ là ước lượng màu, mang tính tham khảo để bạn theo dõi và trao đổi với bác sĩ thú y.\n\nKhi bé có dấu hiệu bạn thấy lo lắng, hãy đưa bé đến cơ sở thú y.',
     '["about","disclaimer"]'::jsonb, 'PUBLISHED', 'NONE', 40, now()),
    (uuidv7(), 'luu-y-khi-ganh-dau-trieu-chung',     uuidv7(), 'vi', 'FAQ',     'GENERAL',
     'Tôi nên ghi chú những gì trong hồ sơ bé?',
     'Các loại ghi chú CatCheck gợi ý, dùng để bác sĩ thú y đọc.',
     E'Bạn có thể ghi chú về thay đổi thức ăn, đổi loại cát, lần đi khám, hoặc những quan sát bạn thấy bất thường.\n\nGhi chú chỉ là thông tin bạn muốn lưu lại. CatCheck không dùng ghi chú để tự suy luận bệnh.',
     '["notes","general"]'::jsonb, 'PUBLISHED', 'NONE', 50, now())
ON CONFLICT (slug, locale) DO UPDATE
    SET title       = EXCLUDED.title,
        summary     = EXCLUDED.summary,
        body_md     = EXCLUDED.body_md,
        tags        = EXCLUDED.tags,
        updated_at  = now();
