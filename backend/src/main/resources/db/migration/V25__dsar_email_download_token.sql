ALTER TABLE dsar_export_job ADD COLUMN email_download_token_hash CHAR(64);

COMMENT ON COLUMN dsar_export_job.email_download_token_hash IS
    'SHA-256 of the separate one-time token sent in PRIVACY_EXPORT_READY email; no raw token is stored in this column.';
