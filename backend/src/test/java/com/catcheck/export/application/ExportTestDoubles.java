package com.catcheck.export.application;

import com.catcheck.export.domain.port.PdfRenderer;
import com.catcheck.export.domain.port.SubjectSnapshotPort;
import com.catcheck.insight.api.HealthFlagExportQuery;
import com.catcheck.scan.api.ScanHistoryQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bộ đôi test dùng chung cho các test của module {@code export}. */
final class ExportTestDoubles {

    private ExportTestDoubles() {
    }

    static SubjectSnapshotPort catOwnedBy(UUID catId, UUID ownerId) {
        return new SubjectSnapshotPort() {
            @Override
            public Optional<CatProfile> findCatProfile(UUID id) {
                return id.equals(catId)
                        ? Optional.of(new CatProfile(catId, ownerId, "Miu", "Muop", "FEMALE",
                                null, 36, new BigDecimal("4.1"), null))
                        : Optional.empty();
            }

            @Override
            public Optional<OwnerProfile> findOwnerProfile(UUID userId) {
                return Optional.of(new OwnerProfile(ownerId, "Chu nuoi", "vi", "Asia/Ho_Chi_Minh"));
            }
        };
    }

    static ScanHistoryQuery oneScanAt(Instant capturedAt) {
        return new ScanHistoryQuery() {
            @Override
            public List<RecentScan> recentValidScans(UUID catId, Instant since, int limit) {
                return List.of();
            }

            @Override
            public List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to) {
                return List.of(new ExportScanRow(UUID.randomUUID(), capturedAt,
                        new BigDecimal("6.4"), "IN_RANGE", new BigDecimal("0.820"),
                        false, false, "NONE", List.of(), false));
            }
        };
    }

    static HealthFlagExportQuery noFlags() {
        return (catId, from, to) -> List.of();
    }

    /** Render giả: trả đúng số byte cố định để test khẳng định được {@code file_bytes}. */
    static PdfRenderer pdfOf(int bytes, int pages) {
        return xhtml -> new PdfRenderer.Result(new byte[bytes], pages);
    }

    /** {@link com.catcheck.export.domain.port.ReportStorage} trong bộ nhớ. */
    static final class InMemoryReportStorage implements com.catcheck.export.domain.port.ReportStorage {

        private final java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        private int counter;

        @Override
        public String provider() {
            return "LOCAL";
        }

        @Override
        public Stored put(String documentCode, byte[] pdf) {
            String ref = "2026/10/" + documentCode.toLowerCase(java.util.Locale.ROOT) + "-" + (++counter) + ".pdf";
            files.put(ref, pdf);
            return new Stored(provider(), ref, pdf.length);
        }

        @Override
        public java.util.Optional<java.io.InputStream> open(String fileRef) {
            return java.util.Optional.ofNullable(files.get(fileRef)).map(java.io.ByteArrayInputStream::new);
        }

        @Override
        public void delete(String fileRef) {
            files.remove(fileRef);
        }
    }
}
