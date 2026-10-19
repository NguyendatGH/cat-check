package com.catcheck.privacy.application.export;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.shared.i18n.MessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataExportJobServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    @Test
    void enqueueReturnsHighEntropyTokenButPersistsOnlyItsSha256Digest() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DsarRequest request = new DsarRequest(requestId, "DSAR-2026-000002", userId, "user@example.test",
                DsarRequestType.ACCESS_EXPORT, DsarChannel.SELF_SERVICE, DsarStatus.RECEIVED,
                NOW, "EMAIL_OTP", NOW, NOW.plusSeconds(86_400), null, NOW.plusSeconds(864_000),
                null, null, false, null, null, null, null, null, null, null, NOW);
        DsarExportJobPort jobs = mock(DsarExportJobPort.class);
        DsarRequestPort requests = mock(DsarRequestPort.class);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        DataExportJobService service = new DataExportJobService(jobs, mock(DsarExportSnapshotPort.class),
                mock(DsarExportStorage.class), requests, mock(org.springframework.core.task.TaskExecutor.class),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(JdbcTemplate.class), mock(AuditLogService.class),
                mock(com.catcheck.notification.api.NotificationGateway.class), messages(), "http://localhost:5173", transactionManager());

        String rawToken = service.enqueue(requestId, RequestEvidence.system());
        String expectedHash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));

        assertEquals(43, rawToken.length(), "32 random bytes encoded as unpadded base64url");
        verify(jobs).enqueue(eq(requestId), eq(expectedHash));
    }

    @Test
    void generateWritesPersonalDataZipAndMarksRequestComplete() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DsarRequest request = new DsarRequest(requestId, "DSAR-2026-000001", userId, "user@example.test",
                DsarRequestType.ACCESS_EXPORT, DsarChannel.SELF_SERVICE, DsarStatus.IN_PROGRESS,
                NOW, "SESSION", NOW, NOW.plusSeconds(86_400), null, NOW.plusSeconds(864_000),
                null, null, false, null, null, null, null, null, null, null, NOW);
        DsarExportJobPort jobs = mock(DsarExportJobPort.class);
        DsarRequestPort requests = mock(DsarRequestPort.class);
        DsarExportSnapshotPort snapshots = mock(DsarExportSnapshotPort.class);
        DsarExportStorage storage = mock(DsarExportStorage.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var notifications = mock(com.catcheck.notification.api.NotificationGateway.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(userId))).thenReturn(List.of("en"));
        when(jobs.claim(requestId)).thenReturn(true);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(snapshots.profile(userId)).thenReturn("{\"email\":\"user@example.test\"}");
        when(snapshots.scans(userId)).thenReturn(List.of("{\"id\":\"s1\",\"cat_id\":\"c1\",\"captured_at\":\"2026-10-04T09:00:00Z\",\"status\":\"ANALYZED\",\"ph_value\":6.5,\"classification\":\"IN_RANGE\",\"confidence\":0.9}"));
        when(snapshots.cats(userId)).thenReturn(List.of());
        when(snapshots.credits(userId)).thenReturn(List.of());
        when(snapshots.consents(userId)).thenReturn(List.of());
        when(snapshots.notifications(userId)).thenReturn(List.of());
        when(snapshots.orders(userId)).thenReturn(List.of());
        String aiMessage = "{\"conversation_id\":\"c1\",\"role\":\"USER\",\"content\":\"hello\"}";
        when(snapshots.aiMessages(userId)).thenReturn(List.of(aiMessage));
        final byte[][] archive = new byte[1][];
        when(storage.put(eq(request.publicRef()), any(byte[].class))).thenAnswer(invocation -> {
            archive[0] = invocation.getArgument(1);
            return "2026/10/DSAR-2026-000001.zip";
        });

        DataExportJobService service = new DataExportJobService(jobs, snapshots, storage, requests,
                mock(org.springframework.core.task.TaskExecutor.class), Clock.fixed(NOW, ZoneOffset.UTC), jdbc,
                mock(AuditLogService.class), notifications, messages(), "http://localhost:5173", transactionManager());
        service.generate(requestId);

        assertTrue(archive[0] != null && archive[0].length > 0);
        Map<String, String> entries = zipEntries(archive[0]);
        assertEquals(List.of("profile.json", "cats.json", "scans.json", "scans.csv", "credits.csv",
                "consents.json", "notifications.json", "orders.json", "ai_conversations.json", "README.en.md"),
                List.copyOf(entries.keySet()));
        assertEquals("[" + aiMessage + "]", entries.get("ai_conversations.json"));
        assertTrue(entries.get("README.en.md").startsWith("# CatCheck personal data package\n"));
        verify(requests).update(any(DsarRequest.class));
        // Email "gói dữ liệu sẵn sàng" đi theo ngôn ngữ của chủ tài khoản, không cố định vi.
        verify(notifications).enqueueTransactionalEmail(org.mockito.ArgumentMatchers.argThat(
                email -> "en".equals(email.locale()) && "No attachments.".equals(email.payload().get("attachments"))));
        verify(jobs, org.mockito.Mockito.never()).fail(any(UUID.class), anyString());
    }

    @Test
    void generateFallsBackToVietnameseWhenUserHasNoLocale() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DsarRequest request = new DsarRequest(requestId, "DSAR-2026-000003", userId, "user@example.test",
                DsarRequestType.ACCESS_EXPORT, DsarChannel.SELF_SERVICE, DsarStatus.IN_PROGRESS,
                NOW, "SESSION", NOW, NOW.plusSeconds(86_400), null, NOW.plusSeconds(864_000),
                null, null, false, null, null, null, null, null, null, null, NOW);
        DsarExportJobPort jobs = mock(DsarExportJobPort.class);
        DsarRequestPort requests = mock(DsarRequestPort.class);
        DsarExportSnapshotPort snapshots = mock(DsarExportSnapshotPort.class);
        DsarExportStorage storage = mock(DsarExportStorage.class);
        var notifications = mock(com.catcheck.notification.api.NotificationGateway.class);
        when(jobs.claim(requestId)).thenReturn(true);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(snapshots.profile(userId)).thenReturn("{}");
        final byte[][] archive = new byte[1][];
        when(storage.put(eq(request.publicRef()), any(byte[].class))).thenAnswer(invocation -> {
            archive[0] = invocation.getArgument(1);
            return "2026/10/DSAR-2026-000003.zip";
        });

        new DataExportJobService(jobs, snapshots, storage, requests,
                mock(org.springframework.core.task.TaskExecutor.class), Clock.fixed(NOW, ZoneOffset.UTC),
                mock(JdbcTemplate.class), mock(AuditLogService.class), notifications, messages(),
                "http://localhost:5173", transactionManager()).generate(requestId);

        assertTrue(zipEntries(archive[0]).get("README.vi.md").startsWith("# Gói dữ liệu cá nhân CatCheck\n"));
        verify(notifications).enqueueTransactionalEmail(org.mockito.ArgumentMatchers.argThat(
                email -> "vi".equals(email.locale()) && "Không có tệp đính kèm.".equals(email.payload().get("attachments"))));
        verify(jobs, org.mockito.Mockito.never()).fail(any(UUID.class), anyString());
    }

    private static Map<String, String> zipEntries(byte[] archive) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static MessageResolver messages() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames("classpath:messages/privacy");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        return new MessageResolver(source);
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return manager;
    }
}
