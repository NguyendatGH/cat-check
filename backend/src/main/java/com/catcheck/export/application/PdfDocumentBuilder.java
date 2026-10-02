package com.catcheck.export.application;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Dựng chuỗi XHTML cho hồ sơ PDF, đúng thứ tự khối bắt buộc của p13 §13.3: (1) bìa, (2) disclaimer
 * PDF-HEADER + EMERGENCY, (3) tóm tắt, (4) biểu đồ, (5) bảng chi tiết, (6) giải thích phương pháp,
 * (7) chân trang mọi trang (CSS {@code running()} — GCPM mà openhtmltopdf hỗ trợ, p13 §13.7).
 *
 * <p>Câu chữ disclaimer (PDF-HEADER/EMERGENCY/PDF-FOOTER) chép NGUYÊN VĂN từ p15 §15.7.3 — p13
 * §13.3 Khối 2 bắt buộc "dùng nguyên văn, không viết lại". Đây là bản nháp owner/luật sư chưa
 * duyệt cuối cùng (ghi rõ trong p15), nhưng là bản DUY NHẤT có trong SPEC — không tự viết lại.</p>
 */
final class PdfDocumentBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");

    private static final String PDF_HEADER = """
            <strong>Về tài liệu này.</strong> Đây là bản ghi do chủ nuôi tự thực hiện tại nhà bằng \
            ứng dụng CatCheck, dựa trên quan sát màu của hạt chỉ thị trong cát vệ sinh. \
            <strong>Đây không phải kết quả xét nghiệm của cơ sở thú y</strong> và không thay thế \
            thăm khám lâm sàng hay xét nghiệm. Giá trị pH hiển thị là <strong>ước lượng từ ảnh \
            chụp</strong>, có thể sai lệch do ánh sáng, thiết bị chụp và điều kiện môi trường. \
            Việc gán kết quả cho từng cá thể mèo do <strong>chủ nuôi tự thực hiện</strong>; hệ \
            thống không tự nhận diện được con mèo nào đã sử dụng khay.""";

    private static final String EMERGENCY_TITLE = "⚠️ Những dấu hiệu cần đi thú y NGAY — đừng chờ theo dõi thêm";
    private static final String EMERGENCY_BODY = """
            Hãy đưa bé đến cơ sở thú y <strong>ngay lập tức</strong> nếu bạn thấy: bé <strong>rặn \
            nhiều nhưng không ra nước tiểu</strong> hoặc chỉ ra vài giọt; bé <strong>kêu, gào, tỏ ra \
            đau</strong> khi đi vệ sinh; bé <strong>ra vào khay liên tục</strong> mà khay vẫn khô; \
            nước tiểu <strong>có máu</strong>; bé <strong>bỏ ăn, nôn, nằm li bì, bụng căng \
            cứng</strong>. Tắc nghẽn đường tiểu là <strong>tình trạng cấp cứu</strong> và có thể \
            nguy hiểm đến tính mạng <strong>trong vòng 24-48 giờ</strong>. Trong những trường hợp \
            này, <strong>đừng dùng CatCheck để chờ theo dõi thêm</strong> — hãy gọi cho phòng khám \
            thú y gần nhất.""";

    private static final String PDF_FOOTER_LINE1 = "CatCheck — dữ liệu theo dõi tại nhà, mang tính "
            + "tham khảo. Không phải kết quả xét nghiệm. Không thay thế bác sĩ thú y.";

    private PdfDocumentBuilder() {
    }

    static String build(ExportRenderData data) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>").append(css()).append("</style></head><body>");

        // Chân trang mọi trang - phần tử position:running(), CSS Paged Media (p13 §13.7).
        html.append("<div id=\"pdf-footer\">")
                .append("<div class=\"footer-line1\">").append(PDF_FOOTER_LINE1)
                .append(" | Xuất ngày ").append(escape(data.generatedAtLocal().format(DATE_FMT)))
                .append(" | ").append(escape(data.documentCode())).append("</div>")
                .append("<div class=\"footer-line2\"><span>Trang <span class=\"page-number\"></span></span>"
                        + "<span>Mã tài liệu: ").append(escape(data.documentCode())).append("</span>"
                        + "<span>CatCheck · catcheck.petcare@gmail.com · (+84) 842551312</span></div>")
                .append("</div>");

        html.append(buildCover(data));
        html.append(buildDisclaimerBlock());
        html.append(buildSummary(data));
        html.append(buildChart(data));
        html.append(buildTable(data));
        html.append(buildFlags(data));
        html.append(buildMethodology(data));

        html.append("</body></html>");
        return html.toString();
    }

    private static String buildCover(ExportRenderData d) {
        return """
                <section class="cover">
                  <div class="cover-header">
                    <span class="logo">CatCheck</span>
                    <span class="doc-code">Mã tài liệu: %s</span>
                  </div>
                  <h1>HỒ SƠ THEO DÕI SỨC KHỎE TIẾT NIỆU</h1>
                  <p class="subtitle">(CatCheck)</p>
                  <table class="cover-table">
                    <tr><td class="label">Mèo</td><td>%s</td></tr>
                    <tr><td class="label">Giống</td><td>%s</td></tr>
                    <tr><td class="label">Giới tính</td><td>%s</td></tr>
                    <tr><td class="label">Tuổi</td><td>%s</td></tr>
                    <tr><td class="label">Cân nặng</td><td>%s</td></tr>
                    <tr><td class="label">Chủ nuôi</td><td>%s</td></tr>
                    <tr><td class="label">Khoảng thời gian</td><td>%s – %s</td></tr>
                    <tr><td class="label">Ngày xuất báo cáo</td><td>%s (giờ Việt Nam)</td></tr>
                  </table>
                </section>
                """.formatted(
                escape(d.documentCode()), escape(d.catName()), escape(nullToDash(d.breedLabel())),
                escape(nullToDash(d.sexLabel())), escape(nullToDash(d.ageLabel())),
                escape(nullToDash(d.weightLabel())), escape(nullToDash(d.ownerName())),
                d.rangeFrom().format(DATE_FMT), d.rangeTo().format(DATE_FMT),
                d.generatedAtLocal().format(DATETIME_FMT));
    }

    private static String buildDisclaimerBlock() {
        return """
                <section class="disclaimer-block">
                  <div class="disclaimer-header"><span class="icon">ⓘ</span> %s</div>
                  <div class="emergency-block">
                    <div class="emergency-title">%s</div>
                    <div>%s</div>
                  </div>
                </section>
                <div style="page-break-after: always;"></div>
                """.formatted(PDF_HEADER, EMERGENCY_TITLE, EMERGENCY_BODY);
    }

    private static String buildSummary(ExportRenderData d) {
        String outOfRangeLine = d.nearBoundaryCount() > 0
                ? "%d lần ngoài khoảng tham chiếu, ngoài ra %d lần sát ranh giới".formatted(d.outOfRangeCount(), d.nearBoundaryCount())
                : "%d lần ngoài khoảng tham chiếu".formatted(d.outOfRangeCount());
        return """
                <section class="summary">
                  <h2>Tóm tắt</h2>
                  <p>%d lần đo trong %d ngày. pH dao động %s–%s, trung vị %s. %s.</p>
                </section>
                """.formatted(d.validCount(), java.time.temporal.ChronoUnit.DAYS.between(d.rangeFrom(), d.rangeTo()) + 1,
                nullToDash(str(d.minPh())), nullToDash(str(d.maxPh())), nullToDash(str(d.medianPh())), outOfRangeLine);
    }

    private static String buildChart(ExportRenderData d) {
        return """
                <section class="chart-section">
                  <h2>Biểu đồ pH theo thời gian</h2>
                  %s
                </section>
                """.formatted(d.svgChart());
    }

    private static String buildTable(ExportRenderData d) {
        String rows = d.scans().stream().map(row -> """
                <tr>
                  <td>%s</td>
                  <td>%s</td>
                  <td>%s %s%s</td>
                  <td>%s</td>
                  <td>%s</td>
                </tr>
                """.formatted(
                        escape(row.capturedAtLabel()),
                        nullToDash(str(row.phValue())),
                        escape(row.classificationSymbol()), escape(row.classificationLabel()),
                        row.disputed() ? " <span class=\"disputed\">(chủ nuôi cho là không chính xác)</span>" : "",
                        escape(row.confidenceLabel()),
                        row.qualityFlagLabel() == null ? "—" : escape(row.qualityFlagLabel())))
                .collect(Collectors.joining());

        return """
                <section class="table-section">
                  <h2>Bảng chi tiết từng lần đo</h2>
                  <table class="scan-table">
                    <thead><tr><th>Ngày giờ</th><th>pH</th><th>Phân loại</th><th>Độ tin cậy</th><th>Cảnh báo chất lượng</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </section>
                """.formatted(rows);
    }

    private static String buildFlags(ExportRenderData d) {
        if (d.flags().isEmpty()) {
            return "";
        }
        String items = d.flags().stream().map(f -> "<li>" + escape(f.explanationVi()) + "</li>").collect(Collectors.joining());
        return """
                <section class="flags-section">
                  <h2>Dấu hiệu hệ thống đã đánh dấu</h2>
                  <ul>%s</ul>
                </section>
                """.formatted(items);
    }

    private static String buildMethodology(ExportRenderData d) {
        String conditionalLine = d.anyNonCardCcm()
                ? "<p><em>Một số lần đo trong kỳ được thực hiện không có thẻ tham chiếu trong khung "
                        + "hình; những lần đó có độ tin cậy thấp hơn và được đánh dấu ở cột \"Cảnh báo "
                        + "chất lượng\".</em></p>"
                : "";
        return """
                <section class="methodology">
                  <h2>Phương pháp đo</h2>
                  <p>Chủ nuôi chụp ảnh vùng cát đổi màu sau khi mèo đi vệ sinh, cùng khung hình với \
                  thẻ màu tham chiếu in kèm bao bì CatCheck. Hệ thống hiệu chỉnh màu theo thẻ tham \
                  chiếu (khi có), đối chiếu màu hạt chỉ thị với bảng ánh xạ màu → pH (phiên bản %s), \
                  rồi ước lượng giá trị pH.</p>
                  %s
                  <p><strong>Giới hạn của phương pháp:</strong></p>
                  <ul>
                    <li>Đây là ước lượng qua màu sắc quan sát bằng camera điện thoại, không phải phép \
                    đo hoá học trực tiếp như que thử pH hoặc xét nghiệm nước tiểu tại phòng thí nghiệm.</li>
                    <li>Kết quả phụ thuộc điều kiện ánh sáng, góc chụp, và việc đặt đúng thẻ màu tham \
                    chiếu trong khung hình lúc chụp.</li>
                    <li>Hệ thống chỉ đo pH, không phát hiện máu, protein, tinh thể hay các thành phần \
                    khác trong nước tiểu.</li>
                    <li>Hệ thống không tự xác định con mèo nào vừa đi vệ sinh — độ tin cậy của dữ liệu \
                    phụ thuộc vào việc chủ nuôi gắn đúng kết quả cho đúng con mèo.</li>
                    <li>Khoảng tham chiếu 6,3–6,6 áp dụng cho pH nước tiểu mèo khoẻ mạnh nói chung; \
                    từng cá thể có thể có mức nền khác nhau, bác sĩ nên đối chiếu với bệnh sử riêng \
                    của mèo.</li>
                  </ul>
                </section>
                """.formatted(d.chartVersionSnapshot() == null ? "chưa hiệu chuẩn (placeholder)" : d.chartVersionSnapshot(),
                conditionalLine);
    }

    private static String css() {
        return """
                @page {
                    size: A4;
                    margin: 20mm 18mm;
                    @bottom-center { content: element(footer); }
                }
                #pdf-footer { position: running(footer); }
                .page-number:before { content: counter(page) "/" counter(pages); }
                body { font-family: 'DejaVu Sans', sans-serif; font-size: 10.5pt; color: #1f2937; }
                h1 { font-size: 18pt; margin-bottom: 2px; }
                h2 { font-size: 13pt; margin-top: 18px; border-bottom: 1px solid #d1d5db; padding-bottom: 3px; }
                .subtitle { color: #6b7280; margin-top: 0; }
                .cover-header { display: flex; justify-content: space-between; font-size: 9pt; color: #6b7280; }
                .logo { font-weight: bold; color: #1f6f4a; }
                .cover-table { margin-top: 12px; border-collapse: collapse; }
                .cover-table td { padding: 3px 10px 3px 0; vertical-align: top; }
                .cover-table td.label { color: #6b7280; white-space: nowrap; }
                .disclaimer-block { background: #f3f6f4; border: 1px solid #b7d4c3; padding: 10px 14px; margin-top: 10px; }
                .disclaimer-header { font-size: 10.5pt; }
                .emergency-block { border: 2px solid #b45309; background: #fff7ed; padding: 8px 12px; margin-top: 10px; }
                .emergency-title { font-weight: bold; color: #9a3412; margin-bottom: 4px; }
                .summary p { line-height: 1.5; }
                .scan-table { width: 100%; border-collapse: collapse; font-size: 9.5pt; }
                .scan-table th, .scan-table td { border: 1px solid #d1d5db; padding: 4px 6px; text-align: left; }
                .scan-table thead { display: table-header-group; }
                .disputed { color: #b45309; font-style: italic; }
                #pdf-footer { font-size: 8.5pt; color: #6b7280; text-align: center; border-top: 1px solid #e5e7eb; padding-top: 4px; }
                .footer-line2 { display: flex; justify-content: space-between; margin-top: 2px; }
                """;
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
