package com.catcheck.scan.tooling;

import com.catcheck.scan.domain.color.PhBandClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GĐ4 — regression test CI-friendly cho {@link PipelineAccuracyHarness}.
 *
 * <p><b>Đây KHÔNG phải test đo độ chính xác</b> (không có ảnh thật từ GĐ1 — xem
 * {@code docs/scan-production-plan.md} mục GĐ4). Mục đích duy nhất: phát hiện wiring của
 * {@code ColorPipelineService}/{@code OpenCvVisionEngine}/{@code OpenCvRawImageProcessor}/
 * {@code ChartCatalog} bị hỏng (ví dụ đổi chữ ký, đổi tên cột DB, đổi ngưỡng làm pipeline luôn
 * trả {@code INCONCLUSIVE}) — chạy trên vài ảnh TỔNG HỢP sinh bởi
 * {@link SyntheticSandDatasetGenerator}, khớp đúng bảng màu ACTIVE hiện có trong Postgres local
 * (self-consistency, không phải ảnh thật).
 *
 * <p>Cần Postgres local đang chạy với bảng màu ACTIVE (vd {@code CHART-PLACEHOLDER-v1}) — cùng
 * tiền đề mà mọi test tích hợp khác của repo này đòi hỏi (xem {@code backend/README.md}, không
 * Docker/Testcontainers).
 */
class PipelineAccuracyHarnessTest {

    @Test
    @DisplayName("Thu muc anh/CSV rong hoac khong ton tai: bao '0 anh, chua co du lieu that', khong crash")
    void emptyInputsReportNoDataWithoutCrashing(@TempDir Path tempDir) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);

        PipelineAccuracyHarness.Report emptyDir = PipelineAccuracyHarness.evaluate(
                tempDir, tempDir.resolve("khong-ton-tai.csv"), out);
        assertThat(emptyDir.noData()).isTrue();
        assertThat(emptyDir.evaluated()).isZero();

        Path missingDir = tempDir.resolve("khong-ton-tai");
        PipelineAccuracyHarness.Report missingDirReport = PipelineAccuracyHarness.evaluate(
                missingDir, tempDir.resolve("khong-ton-tai.csv"), out);
        assertThat(missingDirReport.noData()).isTrue();

        String printed = captured.toString(StandardCharsets.UTF_8);
        assertThat(printed).contains("0 anh, chua co du lieu that");

        String rendered = PipelineAccuracyHarness.renderMarkdown(emptyDir);
        assertThat(rendered).contains("0 anh, chua co du lieu that");
    }

    @Test
    @DisplayName("Self-consistency: 3 anh TONG HOP khop bang mau ACTIVE chay het pipeline, khong INCONCLUSIVE toan bo")
    void syntheticSelfConsistencyRegression(@TempDir Path tempDir) throws Exception {
        Path csv = SyntheticSandDatasetGenerator.generate(tempDir, 1, 7L);
        // Chi giu 3 dong dau (3 moc pH) de test nhanh, dung cho CI - khong phai do chinh xac.
        var allLines = Files.readAllLines(csv);
        Files.write(csv, allLines.subList(0, Math.min(4, allLines.size())));

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        PipelineAccuracyHarness.Report report = PipelineAccuracyHarness.evaluate(tempDir, csv, out);

        assertThat(report.noData()).isFalse();
        assertThat(report.missingImages()).isZero();
        assertThat(report.failures())
                .as("khong anh nao duoc nem loi nghiep vu/IO - wiring pipeline con nguyen")
                .isZero();
        assertThat(report.evaluated()).isEqualTo(3);
        assertThat(report.chartIsPlaceholder())
                .as("bang mau local hien tai van la placeholder (p6 GD3 chua chay)")
                .isTrue();
        // KHONG phai MOI anh phai duoc ANALYZED (tier B cap 0.60, co the co anh INCONCLUSIVE),
        // nhung wiring hong la ca 3 anh deu INCONCLUSIVE dong loat -> bat duoc regression do.
        assertThat(report.inconclusiveCount())
                .as("ca 3 anh deu INCONCLUSIVE la dau hieu wiring hong (xem harness Javadoc)")
                .isLessThan(report.evaluated());

        for (PipelineAccuracyHarness.SampleResult sample : report.samples()) {
            if (!sample.inconclusive() && sample.predictedPh() != null) {
                assertThat(Math.abs(sample.predictedPh() - sample.truePh()))
                        .as("anh tong hop sinh KHOP mau bang - sai so phai rat nho (self-consistency)")
                        .isLessThan(0.5);
                assertThat(sample.predictedBand())
                        .as("band du doan phai khop band ground truth cho anh tu-khop mau")
                        .isEqualTo(sample.trueBand());
            }
        }

        String rendered = PipelineAccuracyHarness.renderMarkdown(report);
        assertThat(rendered).contains("self-consistency");
        assertThat(rendered)
                .as("band hop le phai la mot trong 6 ma cua PhBandClassifier.Code")
                .contains(PhBandClassifier.Code.IN_RANGE.name());
    }
}
