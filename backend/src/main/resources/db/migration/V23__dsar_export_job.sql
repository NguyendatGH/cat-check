-- Dedicated lifecycle for personal-data ZIP exports (separate from ReportPdfJob).
CREATE TABLE dsar_export_job (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    dsar_request_id UUID NOT NULL UNIQUE REFERENCES dsar_request(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    storage_key TEXT,
    expires_at TIMESTAMPTZ,
    downloaded_at TIMESTAMPTZ,
    last_error_code VARCHAR(48),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_dsar_export_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'EXPIRED', 'DOWNLOADED')),
    CONSTRAINT ck_dsar_export_job_attempts CHECK (attempt_count BETWEEN 0 AND 3),
    CONSTRAINT ck_dsar_export_job_completed CHECK (status NOT IN ('COMPLETED', 'DOWNLOADED', 'EXPIRED') OR (storage_key IS NOT NULL AND expires_at IS NOT NULL))
);
CREATE INDEX ix_dsar_export_job_queue ON dsar_export_job(created_at) WHERE status = 'QUEUED';
CREATE INDEX ix_dsar_export_job_expiry ON dsar_export_job(expires_at) WHERE status = 'COMPLETED';
CREATE TRIGGER trg_dsar_export_job_updated_at BEFORE UPDATE ON dsar_export_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, UPDATE ON dsar_export_job TO catcheck_app;
    END IF;
END $$;
