-- V14: Xuat ho so PDF cho bac si thu y - module `export` (p7 §7.2.3).
--
-- Nguon dac ta: spec/parts/p4-domain-model-erd.md §4.9.2 (dong V14) - "export_job", phu thuoc V8
-- (cat). Chi tiet bang: p4 G1. Trang thai QUEUED|RUNNING|READY|FAILED|EXPIRED la ban p4 chot
-- (p13 §13.6.2 dang dung PENDING|PROCESSING - p4 la nguon su that, xem p4 G1 ghi chu nghiep vu
-- cuoi cung).

CREATE TABLE IF NOT EXISTS export_job
(
    id                       UUID          NOT NULL DEFAULT uuidv7(),
    user_id                  UUID          NOT NULL,
    cat_id                   UUID          NOT NULL,
    format                   VARCHAR(8)    NOT NULL DEFAULT 'PDF',
    document_code            VARCHAR(24)   NOT NULL,
    range_from               DATE          NOT NULL,
    range_to                 DATE          NOT NULL,
    range_preset             VARCHAR(16),
    sections                 JSONB         NOT NULL DEFAULT '["TREND","SCAN_LOG","NOTES","PROFILE"]',
    locale                   VARCHAR(8)    NOT NULL,
    timezone                 VARCHAR(64)   NOT NULL,
    status                   VARCHAR(12)   NOT NULL DEFAULT 'QUEUED',
    storage_provider         VARCHAR(16),
    file_ref                 TEXT,
    file_bytes               BIGINT,
    page_count               INT,
    scan_count               INT,
    chart_version_snapshot   INT,
    download_count           INT           NOT NULL DEFAULT 0,
    last_downloaded_at       TIMESTAMPTZ,
    failure_reason           TEXT,
    requested_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,
    expires_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_export_job PRIMARY KEY (id),
    CONSTRAINT uq_export_job_document_code UNIQUE (document_code),
    CONSTRAINT ck_export_job_format CHECK (format = 'PDF'),
    CONSTRAINT ck_export_job_range CHECK (range_from <= range_to),
    CONSTRAINT ck_export_job_range_to_not_future CHECK (range_to <= CURRENT_DATE),
    CONSTRAINT ck_export_job_range_preset CHECK (range_preset IS NULL
        OR range_preset IN ('7D', '30D', '90D', 'CUSTOM')),
    CONSTRAINT ck_export_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    CONSTRAINT ck_export_job_storage_provider CHECK (storage_provider IS NULL
        OR storage_provider IN ('LOCAL', 'CLOUDINARY')),
    CONSTRAINT ck_export_job_file_bytes CHECK (file_bytes IS NULL OR file_bytes > 0),
    CONSTRAINT ck_export_job_page_count CHECK (page_count IS NULL OR page_count > 0),
    CONSTRAINT ck_export_job_scan_count CHECK (scan_count IS NULL OR scan_count >= 0),
    CONSTRAINT ck_export_job_download_count CHECK (download_count >= 0)
);

-- p4 G1 danh sach index.
CREATE INDEX IF NOT EXISTS idx_export_job_user_requested
    ON export_job (user_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_export_job_worker_queue
    ON export_job (status, requested_at)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX IF NOT EXISTS idx_export_job_expires
    ON export_job (expires_at)
    WHERE status = 'READY';
-- Moi user chi co mot job dang chay (p13 §13.6.2, p8 EXPORT_JOB_IN_PROGRESS).
CREATE UNIQUE INDEX IF NOT EXISTS uq_export_job_one_active_per_user
    ON export_job (user_id)
    WHERE status IN ('QUEUED', 'RUNNING');

COMMENT ON TABLE export_job IS 'Yeu cau xuat ho so PDF (p4 G1). Mot user chi co 1 job QUEUED/RUNNING cung luc.';
COMMENT ON COLUMN export_job.document_code IS 'Ma in o chan trang PDF, dang CC-EXP-{nam}-{6 so} (p8 §8.1.3).';
COMMENT ON COLUMN export_job.chart_version_snapshot IS 'Phien ban color_chart ap dung luc sinh bao cao (p13 §13.10).';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_export_job_user') THEN
        ALTER TABLE export_job ADD CONSTRAINT fk_export_job_user
            FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_export_job_cat') THEN
        ALTER TABLE export_job ADD CONSTRAINT fk_export_job_cat
            FOREIGN KEY (cat_id) REFERENCES cat (id) ON DELETE RESTRICT;
    END IF;
END
$$;
