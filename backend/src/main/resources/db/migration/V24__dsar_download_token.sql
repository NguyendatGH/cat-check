ALTER TABLE dsar_export_job ADD COLUMN download_token_hash CHAR(64);

COMMENT ON COLUMN dsar_export_job.download_token_hash IS
    'SHA-256 of the random one-time 256-bit bearer token; raw token is returned only in the authenticated C6 response.';
