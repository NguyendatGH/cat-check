package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.application.export.DsarExportJob;
import com.catcheck.privacy.application.export.DsarExportJobPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDsarExportJobAdapter implements DsarExportJobPort {
    private final JdbcTemplate jdbc;

    public JdbcDsarExportJobAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public DsarExportJob enqueue(UUID requestId, String downloadTokenHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO dsar_export_job(id, dsar_request_id, download_token_hash) VALUES (?, ?, ?) ON CONFLICT (dsar_request_id) DO NOTHING", id, requestId, downloadTokenHash);
        return findByRequestId(requestId).orElseThrow();
    }

    @Override
    public Optional<DsarExportJob> findByRequestId(UUID requestId) {
        return jdbc.query("SELECT id, dsar_request_id, status, attempt_count, storage_key, expires_at, downloaded_at FROM dsar_export_job WHERE dsar_request_id = ?",
                (rs, n) -> new DsarExportJob(rs.getObject("id", UUID.class), rs.getObject("dsar_request_id", UUID.class),
                        rs.getString("status"), rs.getInt("attempt_count"), rs.getString("storage_key"),
                        rs.getObject("expires_at", Instant.class), rs.getObject("downloaded_at", Instant.class)), requestId).stream().findFirst();
    }

    @Override
    public boolean claim(UUID requestId) {
        return jdbc.update("UPDATE dsar_export_job SET status='RUNNING', attempt_count=attempt_count+1 WHERE dsar_request_id=? AND status IN ('QUEUED','FAILED') AND attempt_count < 3", requestId) == 1;
    }

    @Override
    public void complete(UUID requestId, String key, Instant expiresAt) {
        jdbc.update("UPDATE dsar_export_job SET status='COMPLETED', storage_key=?, expires_at=?, last_error_code=NULL WHERE dsar_request_id=? AND status='RUNNING'", key, expiresAt, requestId);
    }

    @Override
    public void fail(UUID requestId, String errorCode) {
        jdbc.update("UPDATE dsar_export_job SET status=CASE WHEN attempt_count >= 3 THEN 'FAILED' ELSE 'QUEUED' END, last_error_code=? WHERE dsar_request_id=? AND status='RUNNING'", errorCode, requestId);
    }

    @Override
    public boolean claimDownload(UUID requestId, String tokenHash, Instant now) {
        return jdbc.update("UPDATE dsar_export_job SET status='DOWNLOADED', downloaded_at=? WHERE dsar_request_id=? AND status='COMPLETED' AND expires_at>? AND downloaded_at IS NULL AND (download_token_hash=? OR email_download_token_hash=?)", now, requestId, now, tokenHash, tokenHash) == 1;
    }
}
