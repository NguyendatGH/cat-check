package com.catcheck.privacy.application.export;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dedicated asynchronous DSAR archive flow; deliberately separate from ReportPdfJob. */
@Service
public class DataExportJobService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** Trạng thái job mà L52 được phép đẩy lại hàng đợi — các trạng thái khác giữ nguyên gói đã có. */
    private static final java.util.Set<String> RESTARTABLE_JOB_STATUS = java.util.Set.of("QUEUED", "FAILED");
    private final DsarExportJobPort jobs;
    private final DsarExportSnapshotPort snapshot;
    private final DsarExportStorage storage;
    private final DsarRequestPort requests;
    private final TaskExecutor executor;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final AuditLogService auditLog;
    private final NotificationGateway notificationGateway;
    private final String webBaseUrl;
    private final TransactionTemplate transactionTemplate;

    public DataExportJobService(DsarExportJobPort jobs, DsarExportSnapshotPort snapshot,
                                DsarExportStorage storage, DsarRequestPort requests,
                                @Qualifier("dsarExportExecutor") TaskExecutor executor,
                                Clock clock, JdbcTemplate jdbc, AuditLogService auditLog,
                                NotificationGateway notificationGateway,
                                @Value("${catcheck.privacy.export.web-base-url:http://localhost:5173}") String webBaseUrl,
                                PlatformTransactionManager transactionManager) {
        this.jobs = jobs; this.snapshot = snapshot; this.storage = storage; this.requests = requests;
        this.executor = executor; this.clock = clock; this.jdbc = jdbc; this.auditLog = auditLog;
        this.notificationGateway = notificationGateway;
        this.webBaseUrl = webBaseUrl == null ? "http://localhost:5173" : webBaseUrl.replaceAll("/+$", "");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public String enqueue(UUID requestId, RequestEvidence evidence) {
        DsarRequest request = requests.findById(requestId)
                .filter(r -> r.requestType() == DsarRequestType.ACCESS_EXPORT && r.identityVerifiedAt() != null)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        jobs.enqueue(request.id(), sha256(token));
        requests.update(request.withStatus(com.catcheck.privacy.domain.DsarStatus.IN_PROGRESS));
        auditLog.record(AuditEvent.builder().actor(AuditActor.user(request.userId(), "USER"))
                .subjectUser(request.userId()).action("EXPORT_DATA")
                .requestId(evidence.requestId()).ipAddress(evidence.ipAddress()).userAgent(evidence.userAgent())
                .meta("publicRef", request.publicRef()).meta("requestType", "ACCESS_EXPORT").build());
        dispatchAfterCommit(request.id());
        return token;
    }

    /**
     * L52 — DPO/ADMIN_SUPER sinh gói dữ liệu cá nhân <b>thay mặt</b> chủ thể (p8 §8.4.12 ô
     * L52, ma trận p14 §14.2.2 ô Q10), trả {@code 202}.
     *
     * <p><b>Tái dùng nguyên đường export tự phục vụ</b> — cùng {@code dsar_export_job}, cùng
     * {@link #generate(UUID)}, cùng email {@code PRIVACY_EXPORT_READY}, cùng hạn 72 giờ và
     * link một lần. Dựng một đường thứ hai cho admin nghĩa là có hai nơi quyết định "gói dữ
     * liệu cá nhân gồm những gì" và hai nơi quyết định khi nào nó bị xoá.</p>
     *
     * <p><b>Q10 chặt hơn cột {@code R:} của p8:</b> p8 ghi {@code R:DPO,ADMIN_SUPER} nhưng
     * p14 Q10 cho {@code ADMIN_SUPER} dấu ✅* <i>"chỉ khi là handled_by của yêu cầu đó"</i>.
     * p11/p14 sở hữu miền phân quyền nên điều kiện hẹp hơn thắng.</p>
     *
     * <p><b>Không trả token thô cho admin</b> (có chủ ý): token tải một lần được sinh, chỉ
     * lưu SHA-256 rồi bỏ giá trị thô đi, nên đường tải duy nhất là link trong email
     * {@code PRIVACY_EXPORT_READY} gửi tới {@code dsar_request.contact_email} — nếu trả token
     * cho người gọi thì một DPO tải được trọn bộ dữ liệu cá nhân của người khác mà chủ thể
     * không hề biết, trong khi p15 §15.4.5 đòi "link yêu cầu đăng nhập". Cách giao gói cho
     * chủ thể không đăng nhập được (kênh POST/EMAIL) p8 chưa quy định — handoff H15.172.</p>
     *
     * @return trạng thái job sau lệnh; {@code dispatched = false} nghĩa là đã có job đang
     *         chạy/đã xong nên lệnh này KHÔNG sinh gói thứ hai
     */
    @Transactional
    public AdminDispatch enqueueOnBehalf(UUID requestId, UUID actorId, String actorRole,
                                         String reason, RequestEvidence evidence) {
        DsarRequest request = requests.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        if (request.requestType() != DsarRequestType.ACCESS_EXPORT) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "requestType");
        }
        if (!"DPO".equals(actorRole) && !actorId.equals(request.handledBy())) {
            // p14 Q10: ADMIN_SUPER chỉ xuất được yêu cầu mà chính mình đang xử lý.
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, "handledBy");
        }
        if (request.identityVerifiedAt() == null) {
            // p15 REQ-DSAR-04: không bao giờ xuất dữ liệu chỉ vì "email gửi tới trông giống".
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED, request.publicRef());
        }
        if (request.userId() == null) {
            // user_id đã bị ẩn danh hoá (ON DELETE SET NULL) — không còn dữ liệu nào để xuất.
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "userId");
        }
        Optional<DsarExportJob> existing = jobs.findByRequestId(request.id());
        if (existing.isPresent() && !RESTARTABLE_JOB_STATUS.contains(existing.get().status())) {
            return new AdminDispatch(request.publicRef(), existing.get().status(), false);
        }
        if (existing.isEmpty()) {
            byte[] tokenBytes = new byte[32];
            SECURE_RANDOM.nextBytes(tokenBytes);
            jobs.enqueue(request.id(), sha256(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)));
        }
        if (request.status() != com.catcheck.privacy.domain.DsarStatus.IN_PROGRESS) {
            requests.update(request.withStatus(com.catcheck.privacy.domain.DsarStatus.IN_PROGRESS));
        }
        auditLog.record(AuditEvent.builder()
                .actor("DPO".equals(actorRole)
                        ? AuditActor.dpo(actorId, actorRole) : AuditActor.admin(actorId, actorRole))
                .subjectUser(request.userId())
                .action("EXPORT_DATA")
                .requestId(evidence.requestId()).ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("publicRef", request.publicRef())
                .meta("requestType", "ACCESS_EXPORT")
                .meta("onBehalfOfSubject", true)
                .meta("reason", reason)
                .build());
        dispatchAfterCommit(request.id());
        return new AdminDispatch(request.publicRef(), "QUEUED", true);
    }

    public void generate(UUID requestId) {
        if (!jobs.claim(requestId)) return;
        String storedKey = null;
        try {
            DsarRequest request = requests.findById(requestId)
                    .filter(r -> r.requestType() == DsarRequestType.ACCESS_EXPORT && r.userId() != null)
                    .orElseThrow();
            byte[] zip = archive(request.userId());
            String key = storage.put(request.publicRef(), zip);
            storedKey = key;
            var expiresAt = clock.instant().plus(Duration.ofHours(72));
            byte[] emailTokenBytes = new byte[32];
            SECURE_RANDOM.nextBytes(emailTokenBytes);
            String emailToken = Base64.getUrlEncoder().withoutPadding().encodeToString(emailTokenBytes);
            DsarRequest completed = request.withExportResult(key, expiresAt, clock.instant());
            String downloadLink = webBaseUrl + "/account/privacy#dsar-download?publicRef="
                    + java.net.URLEncoder.encode(request.publicRef(), StandardCharsets.UTF_8)
                    + "&token=" + java.net.URLEncoder.encode(emailToken, StandardCharsets.UTF_8);
            transactionTemplate.executeWithoutResult(status -> {
                jdbc.update("UPDATE dsar_export_job SET status='COMPLETED', storage_key=?, expires_at=?, email_download_token_hash=?, last_error_code=NULL WHERE dsar_request_id=? AND status='RUNNING'", key, expiresAt, sha256(emailToken), requestId);
                requests.update(completed);
                notificationGateway.enqueueTransactionalEmail(new TransactionalEmailRequest(
                        request.contactEmail(), "PRIVACY_EXPORT_READY", "vi",
                        java.util.Map.of("publicRef", request.publicRef(), "downloadUrl", downloadLink,
                                "expiresAt", expiresAt.toString(), "attachments", "Không có tệp đính kèm."),
                        "PRIVACY_EXPORT_READY:" + request.id()));
            });
        } catch (Exception ex) {
            // Do not persist PII or exception messages in operational records/logs.
            if (storedKey != null) storage.delete(storedKey);
            jobs.fail(requestId, "DSAR_EXPORT_GENERATION_FAILED");
            jobs.findByRequestId(requestId).filter(job -> "QUEUED".equals(job.status()))
                    .ifPresent(job -> dispatchAfterCommit(requestId));
        }
    }

    @Transactional
    public Download openDownload(UUID userId, String publicRef, String token, RequestEvidence evidence) {
        DsarRequest request = requests.findByPublicRef(publicRef)
                .filter(r -> userId.equals(r.userId()) && r.requestType() == DsarRequestType.ACCESS_EXPORT)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        if (request.resultRef() == null || request.resultExpiresAt() == null) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_NOT_READY, request.status().name());
        }
        var job = jobs.findByRequestId(request.id()).orElseThrow(
                () -> new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_NOT_READY, request.status().name()));
        if (job.downloadedAt() != null || "DOWNLOADED".equals(job.status())) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_ALREADY_DOWNLOADED, job.downloadedAt());
        }
        if (job.expiresAt() == null || !job.expiresAt().isAfter(clock.instant())) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_EXPIRED, job.expiresAt());
        }
        InputStream stream = storage.open(job.storageKey()).orElseThrow(
                () -> new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_NOT_READY, request.status().name()));
        if (token == null || token.length() > 128 || !jobs.claimDownload(request.id(), sha256(token), clock.instant())) {
            try { stream.close(); } catch (IOException ignored) { }
            throw new BusinessRuleException(PrivacyErrorCode.FORBIDDEN);
        }
        requests.update(request.withResultDownloadedAt(clock.instant()));
        auditLog.record(AuditEvent.builder().actor(AuditActor.user(userId, "USER"))
                .subjectUser(userId).action("EXPORT_DATA").requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress()).userAgent(evidence.userAgent())
                .meta("publicRef", publicRef).meta("download", true).build());
        return new Download(new FilterInputStream(stream) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { storage.delete(job.storageKey()); }
            }
        }, publicRef + ".zip");
    }

    public int cleanupExpired(int limit) {
        Instant now = clock.instant();
        List<ExpiredFile> expired = jdbc.query("SELECT dsar_request_id, storage_key FROM dsar_export_job WHERE status='COMPLETED' AND expires_at<=? ORDER BY expires_at LIMIT ?",
                (rs, row) -> new ExpiredFile(rs.getObject("dsar_request_id", UUID.class), rs.getString("storage_key")), now, limit);
        int cleaned = 0;
        for (ExpiredFile file : expired) {
            storage.delete(file.storageKey());
            int updated = jdbc.update("UPDATE dsar_export_job SET status='EXPIRED' WHERE dsar_request_id=? AND status='COMPLETED' AND expires_at<=?", file.requestId(), now);
            if (updated == 1) {
                requests.findById(file.requestId()).ifPresent(request -> auditLog.record(
                        AuditEvent.builder().actor(AuditActor.job())
                                .subject(request.userId() == null ? AuditSubjectType.JOB : AuditSubjectType.USER, request.userId())
                                .action("DSAR_EXPORT_CLEANUP").meta("requestId", request.id()).build()));
                cleaned++;
            }
        }
        return cleaned;
    }

    private byte[] archive(UUID userId) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, "profile.json", snapshot.profile(userId));
            entry(zip, "cats.json", jsonArray(snapshot.cats(userId)));
            List<String> scans = snapshot.scans(userId);
            entry(zip, "scans.json", jsonArray(scans));
            entry(zip, "scans.csv", csv(scans, List.of("id", "cat_id", "captured_at", "status", "ph_value", "classification", "confidence")));
            entry(zip, "credits.csv", csv(snapshot.credits(userId), List.of("id", "type", "amount", "balance_after", "ref_type", "ref_id", "note", "created_at")));
            entry(zip, "consents.json", jsonArray(snapshot.consents(userId)));
            entry(zip, "notifications.json", jsonArray(snapshot.notifications(userId)));
            entry(zip, "orders.json", jsonArray(snapshot.orders(userId)));
            entry(zip, "README.vi.md", "# Gói dữ liệu cá nhân CatCheck\n\nGói này chứa thông tin hồ sơ, mèo, kết quả quét, credit, consent, thông báo và đơn hàng hiện có trong hệ thống. Ảnh đã hết hạn retention 14 ngày hoặc đã bị xoá không thể khôi phục và không có trong gói này. Nội dung do người khác tạo và dữ liệu cộng đồng không được xuất trong phiên bản hiện tại. Các số điện thoại được mã hoá ở hệ thống không được đưa ra dưới dạng ciphertext.\n");
        }
        return bytes.toByteArray();
    }

    private String jsonArray(List<String> values) { return "[" + String.join(",", values) + "]"; }
    private String csv(List<String> rows, List<String> columns) throws IOException {
        StringBuilder out = new StringBuilder(String.join(",", columns)).append('\n');
        for (String row : rows) {
            JsonNode node = mapper.readTree(row);
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) out.append(',');
                JsonNode value = node.get(columns.get(i));
                if (value != null && !value.isNull()) out.append('"').append(value.asText().replace("\"", "\"\"")).append('"');
            }
            out.append('\n');
        }
        return out.toString();
    }
    private static void entry(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
    private void dispatchAfterCommit(UUID requestId) {
        Runnable dispatch = () -> {
            try { executor.execute(() -> generate(requestId)); }
            catch (TaskRejectedException ignored) { jobs.fail(requestId, "DSAR_EXPORT_QUEUE_FULL"); }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { dispatch.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { dispatch.run(); }
        });
    }
    public record Download(InputStream stream, String filename) { }
    /** Kết quả L52 — {@code dispatched = false} nghĩa là tái dùng gói/job đã có. */
    public record AdminDispatch(String publicRef, String status, boolean dispatched) { }
    private record ExpiredFile(UUID requestId, String storageKey) { }
}
