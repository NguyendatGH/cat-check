package com.catcheck.export.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** M22 — PDF chỉ render các khối tuỳ chọn user đã chọn; khối bắt buộc p13 §13.3 luôn có. */
class PdfDocumentBuilderTest {

    private final PdfDocumentBuilder vi = new PdfDocumentBuilder(ExportTestDoubles.messages(), Locale.forLanguageTag("vi"));
    private final PdfDocumentBuilder en = new PdfDocumentBuilder(ExportTestDoubles.messages(), Locale.forLanguageTag("en"));

    @Test
    @DisplayName("M22 — bỏ TREND/SCAN_LOG/PROFILE thì không có biểu đồ, bảng, hàng hồ sơ; khối bắt buộc vẫn còn")
    void unselectedSectionsAreOmittedButMandatoryBlocksStay() {
        String html = vi.build(data(List.of("NOTES")));

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
        String html = vi.build(data(List.of("TREND", "SCAN_LOG", "PROFILE")));

        assertThat(html)
                .contains("Biểu đồ pH theo thời gian")
                .contains("Bảng chi tiết từng lần đo")
                .contains(">Giống<")
                .contains(">Cân nặng<");
    }

    @Test
    @DisplayName("M22 — sections null (dòng cũ) coi như chọn tất cả")
    void nullSectionsRenderEverything() {
        String html = vi.build(data(null));

        assertThat(html)
                .contains("Biểu đồ pH theo thời gian")
                .contains("Bảng chi tiết từng lần đo")
                .contains(">Giống<");
    }

    @Test
    @DisplayName("i18n — job vi giữ nguyên nhãn, ngày và câu tóm tắt tiếng Việt")
    void vietnameseJobKeepsVietnameseText() {
        String html = vi.build(data(null));

        assertThat(html)
                .contains("Mã tài liệu: CC-EXP-2026-123456")
                .contains("Xuất ngày 03/10/2026")
                .contains("Trang <span class=\"page-number\"></span>")
                .contains("03/10/2026, 11:00 (giờ Việt Nam)")
                .contains("1 lần đo trong 30 ngày. pH dao động 6.4–6.4, trung vị 6.4. 0 lần ngoài khoảng tham chiếu.")
                .contains("Hãy đưa bé đến cơ sở thú y <strong>ngay lập tức</strong>")
                .contains("<th>Ngày giờ</th><th>pH</th><th>Phân loại</th><th>Độ tin cậy</th><th>Cảnh báo chất lượng</th>");
    }

    @Test
    @DisplayName("i18n — job en render nhãn, ngày và câu tóm tắt tiếng Anh, không còn chữ Việt của khung")
    void englishJobRendersEnglishLabels() {
        String html = en.build(data(null));

        assertThat(html)
                .contains("Document code: CC-EXP-2026-123456")
                .contains("Exported on 03 Oct 2026")
                .contains("Page <span class=\"page-number\"></span>")
                .contains("04 Sep 2026 – 03 Oct 2026")
                .contains("03 Oct 2026, 11:00 (Vietnam time)")
                .contains("<h2>Summary</h2>")
                .contains("1 measurement over 30 days. pH ranged 6.4–6.4, median 6.4. "
                        + "0 measurements outside the reference range.")
                .contains("pH trend over time")
                .contains("Detailed table of each measurement")
                .contains("<th>Date and time</th><th>pH</th><th>Category</th><th>Confidence</th><th>Quality warning</th>")
                .contains(">Breed<")
                .contains("Measurement method")
                .contains("Take your cat to a veterinary clinic <strong>immediately</strong>")
                .contains("not yet calibrated (placeholder)")
                .doesNotContain("Mã tài liệu")
                .doesNotContain("Xuất ngày")
                .doesNotContain("Tóm tắt")
                .doesNotContain("Phương pháp đo")
                .doesNotContain("Hãy đưa bé");
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
