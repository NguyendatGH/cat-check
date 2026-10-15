package com.catcheck.export.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** M22 — PDF chỉ render các khối tuỳ chọn user đã chọn; khối bắt buộc p13 §13.3 luôn có. */
class PdfDocumentBuilderTest {

    @Test
    @DisplayName("M22 — bỏ TREND/SCAN_LOG/PROFILE thì không có biểu đồ, bảng, hàng hồ sơ; khối bắt buộc vẫn còn")
    void unselectedSectionsAreOmittedButMandatoryBlocksStay() {
        String html = PdfDocumentBuilder.build(data(List.of("NOTES")));

        assertThat(html)
                .doesNotContain("Biểu đồ pH theo thời gian")
                .doesNotContain("Bảng chi tiết từng lần đo")
                .doesNotContain(">Giống<")
                .doesNotContain(">Cân nặng<")
                .contains("disclaimer-block")
                .contains("emergency-block")
                .contains("Tóm tắt")
                .contains("Phương pháp đo")
                .contains("pdf-footer")
                .contains(">Mèo<")
                .contains("Miu");
    }

    @Test
    @DisplayName("M22 — chọn đủ khối thì render đủ")
    void selectedSectionsAreRendered() {
        String html = PdfDocumentBuilder.build(data(List.of("TREND", "SCAN_LOG", "PROFILE")));

        assertThat(html)
                .contains("Biểu đồ pH theo thời gian")
                .contains("Bảng chi tiết từng lần đo")
                .contains(">Giống<")
                .contains(">Cân nặng<");
    }

    @Test
    @DisplayName("M22 — sections null (dòng cũ) coi như chọn tất cả")
    void nullSectionsRenderEverything() {
        String html = PdfDocumentBuilder.build(data(null));

        assertThat(html)
                .contains("Biểu đồ pH theo thời gian")
                .contains("Bảng chi tiết từng lần đo")
                .contains(">Giống<");
    }

    private static ExportRenderData data(List<String> sections) {
        return new ExportRenderData(
                "CC-EXP-2026-123456", "Miu", "Muop", "Mèo cái", "3 tuổi 0 tháng", "4.1 kg", "Chu nuoi",
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3),
                LocalDateTime.of(2026, 10, 3, 11, 0),
                List.of(new ExportRenderData.ScanRow("03/10/2026 10:00", new BigDecimal("6.4"), "●",
                        "Trong khoảng tham chiếu", "Cao", null, false, true)),
                1, new BigDecimal("6.4"), new BigDecimal("6.4"), new BigDecimal("6.4"), 0, 0,
                "<svg></svg>", false, List.of(), null, null, sections);
    }
}
