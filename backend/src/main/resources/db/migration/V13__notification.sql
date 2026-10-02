-- V13: Nhac lich + thong bao - module `reminder` va `notification` (p7 §7.2.3).
--
-- Nguon dac ta: context/spec/parts/p4-domain-model-erd.md §4.9.2 (dong V13) - "notification,
-- notification_outbox, email_outbox, push_subscription, user_notification_preference,
-- reminder; FK health_flag.notification_id", phu thuoc V8 (cat) va V12 (monitoring).
-- Chi tiet tung bang: p4 F1 (reminder), F2 (notification), F3 (push_subscription),
-- F4 (user_notification_preference), F5 (email_outbox + notification_outbox).
--
-- LUU Y VAN HANH: file nay duoc viet SAU khi V14/V15 da chay tren DB dev, nen Flyway coi no
-- la migration "out of order". `application-local.yml` bat `spring.flyway.out-of-order: true`
-- cho rieng profile local. Moi truong moi (CI, staging, prod) chay tu dau nen thu tu tu nhien
-- V12 -> V13 -> V14, khong can co nay.
--
-- PHAM VI JAVA: M1 chi hien thuc module `reminder` (p8 I1-I6). Nam bang con lai duoc tao DDL
-- day du o day de danh muc migration khong bi lech, nhung chua co code Java doc/ghi - job
-- push/email la mot he thong con rieng (p12), lam sau. Khong xoa bang: them lai o version moi
-- se lam lech danh muc p4 §4.9.2 (danh muc dong bang V1-V17).

-- ---------------------------------------------------------------------------------------
-- F1. reminder - lich nhac theo doi
-- ---------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reminder
(
    id                   UUID        NOT NULL DEFAULT uuidv7(),
    user_id              UUID        NOT NULL,
    cat_id               UUID,
    type                 VARCHAR(24) NOT NULL,
    schedule_kind        VARCHAR(12) NOT NULL DEFAULT 'INTERVAL',
    interval_days        INT,
    rrule                TEXT,
    preferred_time_start TIME,
    preferred_time_end   TIME,
    timezone             VARCHAR(64) NOT NULL,
    next_run_at          TIMESTAMPTZ,
    last_run_at          TIMESTAMPTZ,
    last_satisfied_at    TIMESTAMPTZ,
    channels             JSONB       NOT NULL DEFAULT '["PUSH"]'::jsonb,
    source               VARCHAR(16) NOT NULL DEFAULT 'USER',
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_reminder PRIMARY KEY (id),
    CONSTRAINT ck_reminder_type CHECK (type IN ('SCAN_ROUTINE', 'CREDIT_EXPIRY', 'SURVEY_FOLLOWUP')),
    -- p4 F1: nhac quet dinh ky bat buoc gan voi mot be meo; nhac cap tai khoan thi cat_id NULL.
    CONSTRAINT ck_reminder_scan_needs_cat CHECK (type <> 'SCAN_ROUTINE' OR cat_id IS NOT NULL),
    CONSTRAINT ck_reminder_schedule_kind CHECK (schedule_kind IN ('INTERVAL', 'RRULE')),
    CONSTRAINT ck_reminder_interval CHECK (schedule_kind <> 'INTERVAL' OR (interval_days BETWEEN 1 AND 90)),
    CONSTRAINT ck_reminder_rrule CHECK (schedule_kind <> 'RRULE' OR rrule IS NOT NULL),
    CONSTRAINT ck_reminder_time_window CHECK (
        preferred_time_start IS NULL OR preferred_time_end IS NULL OR preferred_time_end > preferred_time_start),
    CONSTRAINT ck_reminder_source CHECK (source IN ('USER', 'SUGGESTED'))
);

-- Index cua scheduler: job quet next_run_at <= now() moi 5-15 phut. Partial de index chi to
-- bang so reminder dang bat, khong phai toan bang.
CREATE INDEX IF NOT EXISTS ix_reminder_next_run
    ON reminder (next_run_at) WHERE active AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_reminder_user_active
    ON reminder (user_id, active) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_reminder_cat
    ON reminder (cat_id) WHERE active AND deleted_at IS NULL;
-- Bat bien I23: mot be meo chi co MOT lich dang bat cho moi `type`. Day la cho ep
-- REMINDER_LIMIT_REACHED (p8 §8.2.4) - khong ep o tang service.
CREATE UNIQUE INDEX IF NOT EXISTS uq_reminder_cat_type_active
    ON reminder (cat_id, type) WHERE active AND deleted_at IS NULL;

COMMENT ON TABLE reminder IS 'Lich nhac theo doi (p4 F1). Soft delete bang deleted_at.';
COMMENT ON COLUMN reminder.timezone IS 'IANA tz SNAPSHOT luc tao - khong tham chieu dong theo app_user.timezone, de lich da dat khong tu nhay gio khi user doi mui gio.';
COMMENT ON COLUMN reminder.last_satisfied_at IS 'Lan quet gan nhat thoa lich - de tinh "qua han N ngay" o man M2 09.';
COMMENT ON COLUMN reminder.rrule IS 'RFC 5545, chi dung khi schedule_kind=RRULE (vd lich goi y 2 lan/tuan o M1 01c-4).';

-- ---------------------------------------------------------------------------------------
-- F2. notification - thong bao da/dang gui
-- ---------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification
(
    id             UUID        NOT NULL DEFAULT uuidv7(),
    user_id        UUID        NOT NULL,
    channel        VARCHAR(12) NOT NULL,
    template_code  VARCHAR(48) NOT NULL,
    payload        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    title_snapshot TEXT,
    body_snapshot  TEXT,
    status         VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    ref_type       VARCHAR(24),
    ref_id         UUID,
    dedupe_key     VARCHAR(160),
    attempt_count  INT         NOT NULL DEFAULT 0,
    last_error     TEXT,
    scheduled_at   TIMESTAMPTZ,
    sent_at        TIMESTAMPTZ,
    read_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('PUSH', 'EMAIL', 'IN_APP')),
    CONSTRAINT ck_notification_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'SUPPRESSED')),
    CONSTRAINT ck_notification_ref_type CHECK (
        ref_type IS NULL OR ref_type IN ('REMINDER', 'HEALTH_FLAG', 'CREDIT', 'EXPORT', 'SYSTEM')),
    CONSTRAINT ck_notification_attempts CHECK (attempt_count >= 0),
    CONSTRAINT uq_notification_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX IF NOT EXISTS ix_notification_user_created
    ON notification (user_id, created_at DESC);
-- Badge so chua doc tren icon chuong: dem phai nhanh vi chay moi lan vao Home.
CREATE INDEX IF NOT EXISTS ix_notification_unread_inapp
    ON notification (user_id) WHERE read_at IS NULL AND channel = 'IN_APP';
CREATE INDEX IF NOT EXISTS ix_notification_queued
    ON notification (status, scheduled_at) WHERE status = 'QUEUED';
CREATE INDEX IF NOT EXISTS ix_notification_ref
    ON notification (ref_type, ref_id);

COMMENT ON TABLE notification IS 'Ban ghi nghiep vu "user nay da duoc thong bao dieu gi" (p4 F2). Giao van nam o notification_outbox.';

-- ---------------------------------------------------------------------------------------
-- F3. push_subscription - dang ky nhan web push (FCM, quyet dinh #16)
-- ---------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS push_subscription
(
    id              UUID         NOT NULL DEFAULT uuidv7(),
    user_id         UUID         NOT NULL,
    fid             VARCHAR(255),
    legacy_token    VARCHAR(512),
    platform        VARCHAR(16)  NOT NULL DEFAULT 'WEB',
    device_label    VARCHAR(100),
    user_agent      TEXT,
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    last_error_at   TIMESTAMPTZ,
    failure_count   INT          NOT NULL DEFAULT 0,
    revoked_at      TIMESTAMPTZ,
    revoke_reason   VARCHAR(32),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_push_subscription PRIMARY KEY (id),
    -- p8 PUSH_SUBSCRIPTION_INVALID khi thieu ca hai.
    CONSTRAINT ck_push_identifier CHECK (fid IS NOT NULL OR legacy_token IS NOT NULL),
    CONSTRAINT ck_push_platform CHECK (platform IN ('WEB', 'ANDROID_PWA', 'IOS_PWA')),
    CONSTRAINT ck_push_failure_count CHECK (failure_count >= 0),
    CONSTRAINT ck_push_revoke_reason CHECK (
        revoke_reason IS NULL OR revoke_reason IN (
            'FCM_UNREGISTERED', 'FCM_INVALID', 'USER_DISABLED', 'CONSENT_WITHDRAWN',
            'ACCOUNT_DELETION', 'STALE'))
);

-- Mot trinh duyet = mot dong. COALESCE vi giai doan di tru co client chi co legacy_token.
CREATE UNIQUE INDEX IF NOT EXISTS uq_push_subscription_device
    ON push_subscription (user_id, COALESCE(fid, legacy_token));
-- Hai nguoi dung chung may: mot FID chi thuoc MOT user dang hoat dong.
CREATE UNIQUE INDEX IF NOT EXISTS uq_push_subscription_fid_active
    ON push_subscription (fid) WHERE fid IS NOT NULL AND revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_push_subscription_user_active
    ON push_subscription (user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_push_subscription_stale
    ON push_subscription (last_seen_at) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_push_subscription_revoked
    ON push_subscription (revoked_at) WHERE revoked_at IS NOT NULL;

COMMENT ON COLUMN push_subscription.fid IS 'Firebase Installation ID - dinh danh BEN, duong moi uu tien.';
COMMENT ON COLUMN push_subscription.legacy_token IS 'Registration token - no ky thuat co chu dich, deprecate khi moi client dung SDK moi.';

-- ---------------------------------------------------------------------------------------
-- F4. user_notification_preference - tuy chon kenh/tan suat (KHONG phai consent)
-- ---------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_notification_preference
(
    user_id                         UUID        NOT NULL,
    attention_alert_channel         VARCHAR(16) NOT NULL DEFAULT 'PUSH_AND_INAPP',
    credit_alerts_enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    report_ready_enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    image_retention_warning_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    normal_result_enabled           BOOLEAN     NOT NULL DEFAULT FALSE,
    quiet_hours_start               TIME        NOT NULL DEFAULT '22:00',
    quiet_hours_end                 TIME        NOT NULL DEFAULT '07:00',
    quiet_hours_enabled             BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_notification_preference PRIMARY KEY (user_id),
    -- Khong co gia tri "tat": canh bao ket qua can chu y la ly do ton tai cua san pham
    -- (quyet dinh #10), user chi duoc chon kenh.
    CONSTRAINT ck_unp_attention_channel CHECK (attention_alert_channel IN ('PUSH_AND_INAPP', 'INAPP_ONLY'))
);

COMMENT ON TABLE user_notification_preference IS 'Tuy chon KHONG mang tinh phap ly (p4 F4). Tuy chon phap ly nam o consent_record - hai thu khong duoc tron.';

-- ---------------------------------------------------------------------------------------
-- F5. email_outbox + notification_outbox - hang doi gui
-- p12: cam gui truc tiep trong request thread. Service chi INSERT trong cung transaction.
-- ---------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS email_outbox
(
    id              UUID         NOT NULL DEFAULT uuidv7(),
    to_address      VARCHAR(320) NOT NULL,
    template_code   VARCHAR(48)  NOT NULL,
    locale          VARCHAR(8)   NOT NULL DEFAULT 'vi',
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    dedupe_key      VARCHAR(160),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_email_outbox PRIMARY KEY (id),
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_email_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT uq_email_outbox_dedupe UNIQUE (dedupe_key)
);

-- Worker lay viec bang FOR UPDATE SKIP LOCKED.
CREATE INDEX IF NOT EXISTS ix_email_outbox_pending
    ON email_outbox (status, next_attempt_at) WHERE status = 'PENDING';
-- Dead-letter cho admin gui lai thu cong.
CREATE INDEX IF NOT EXISTS ix_email_outbox_failed
    ON email_outbox (status) WHERE status = 'FAILED';

COMMENT ON COLUMN email_outbox.payload IS 'Bien truyen vao template. KHONG chua ma OTP tho - job doc ma tu email_otp luc render.';
COMMENT ON COLUMN email_outbox.to_address IS 'Khong FK sang app_user: email OTP duoc gui truoc khi user ton tai.';

CREATE TABLE IF NOT EXISTS notification_outbox
(
    id                   UUID         NOT NULL DEFAULT uuidv7(),
    notification_id      UUID         NOT NULL,
    push_subscription_id UUID         NOT NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts             INT          NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fcm_message_id       VARCHAR(128),
    last_error_code      VARCHAR(48),
    last_error           TEXT,
    sent_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification_outbox PRIMARY KEY (id),
    CONSTRAINT ck_notification_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_outbox_attempts CHECK (attempts >= 0),
    -- Mot thong bao gui toi da MOT lan toi mot thiet bi.
    CONSTRAINT uq_notification_outbox_target UNIQUE (notification_id, push_subscription_id)
);

CREATE INDEX IF NOT EXISTS ix_notification_outbox_pending
    ON notification_outbox (status, next_attempt_at) WHERE status = 'PENDING';

COMMENT ON TABLE notification_outbox IS 'Co che giao van "da day toi thiet bi nao, thu may lan" (p4 F5). Gop vao notification se khien inbox in-app hien moi thiet bi mot dong.';

-- ---------------------------------------------------------------------------------------
-- Khoa ngoai - dat sau cung de thu tu tao bang khong rang buoc
-- ---------------------------------------------------------------------------------------
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reminder_user') THEN
            ALTER TABLE reminder
                ADD CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_reminder_cat') THEN
            ALTER TABLE reminder
                ADD CONSTRAINT fk_reminder_cat FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notification_user') THEN
            ALTER TABLE notification
                ADD CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_push_subscription_user') THEN
            ALTER TABLE push_subscription
                ADD CONSTRAINT fk_push_subscription_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_unp_user') THEN
            ALTER TABLE user_notification_preference
                ADD CONSTRAINT fk_unp_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notification_outbox_notification') THEN
            ALTER TABLE notification_outbox
                ADD CONSTRAINT fk_notification_outbox_notification
                    FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE CASCADE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notification_outbox_subscription') THEN
            ALTER TABLE notification_outbox
                ADD CONSTRAINT fk_notification_outbox_subscription
                    FOREIGN KEY (push_subscription_id) REFERENCES push_subscription (id) ON DELETE CASCADE;
        END IF;
        -- V12 da tao cot health_flag.notification_id va ghi chu "FK se duoc V13 bo sung".
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_health_flag_notification') THEN
            ALTER TABLE health_flag
                ADD CONSTRAINT fk_health_flag_notification
                    FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE SET NULL;
        END IF;
    END
$$;
