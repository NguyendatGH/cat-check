package com.catcheck.privacy.application.export;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.application.RequestEvidence;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
                mock(com.catcheck.notification.api.NotificationGateway.class), "http://localhost:5173", transactionManager());

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
        when(jobs.claim(requestId)).thenReturn(true);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        when(snapshots.profile(userId)).thenReturn("{\"email\":\"user@example.test\"}");
        when(snapshots.scans(userId)).thenReturn(List.of("{\"id\":\"s1\",\"cat_id\":\"c1\",\"captured_at\":\"2026-10-04T09:00:00Z\",\"status\":\"ANALYZED\",\"ph_value\":6.5,\"classification\":\"IN_RANGE\",\"confidence\":0.9}"));
        when(snapshots.cats(userId)).thenReturn(List.of());
        when(snapshots.credits(userId)).thenReturn(List.of());
        when(snapshots.consents(userId)).thenReturn(List.of());
        when(snapshots.notifications(userId)).thenReturn(List.of());
        when(snapshots.orders(userId)).thenReturn(List.of());
        final byte[][] archive = new byte[1][];
        when(storage.put(eq(request.publicRef()), any(byte[].class))).thenAnswer(invocation -> {
            archive[0] = invocation.getArgument(1);
            return "2026/10/DSAR-2026-000001.zip";
        });

        DataExportJobService service = new DataExportJobService(jobs, snapshots, storage, requests,
                mock(org.springframework.core.task.TaskExecutor.class), Clock.fixed(NOW, ZoneOffset.UTC), jdbc,
                mock(AuditLogService.class), notifications, "http://localhost:5173", transactionManager());
        service.generate(requestId);

        assertTrue(archive[0] != null && archive[0].length > 0);
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive[0]))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) names.add(entry.getName());
        }
        assertEquals(List.of("profile.json", "cats.json", "scans.json", "scans.csv", "credits.csv",
                "consents.json", "notifications.json", "orders.json", "README.vi.md"), names);
        verify(requests).update(any(DsarRequest.class));
        verify(notifications).enqueueTransactionalEmail(any());
        verify(jobs, org.mockito.Mockito.never()).fail(any(UUID.class), anyString());
    }

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return manager;
    }
}
