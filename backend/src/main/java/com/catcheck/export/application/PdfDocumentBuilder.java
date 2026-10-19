package com.catcheck.export.application;

import com.catcheck.shared.i18n.MessageResolver;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

final class PdfDocumentBuilder {

    static final String SECTION_TREND = "TREND";
    static final String SECTION_SCAN_LOG = "SCAN_LOG";
    static final String SECTION_PROFILE = "PROFILE";

    private final MessageResolver messages;
    private final Locale locale;
    private final DateTimeFormatter dateFmt;
    private final DateTimeFormatter dateTimeFmt;

    PdfDocumentBuilder(MessageResolver messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
        String datePattern = t("export.pdf.date_pattern");
        this.dateFmt = DateTimeFormatter.ofPattern(datePattern, locale);
        this.dateTimeFmt = DateTimeFormatter.ofPattern(datePattern + ", HH:mm", locale);
    }

    String build(ExportRenderData data) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>").append(css()).append("</style></head><body>");

        html.append("<div id=\"pdf-footer\">")
                .append("<div class=\"footer-line1\">").append(t("export.pdf.footer.disclaimer"))
                .append(" | ").append(escape(t("export.pdf.footer.exported", data.generatedAtLocal().format(dateFmt))))
                .append(" | ").append(escape(data.documentCode())).append("</div>")
                .append("<div class=\"footer-line2\"><span>").append(t("export.pdf.footer.page")).append("</span>"
                        + "<span>").append(escape(t("export.pdf.doc_code", data.documentCode()))).append("</span>"
                        + "<span>CatCheck · catcheck.petcare@gmail.com · (+84) 842551312</span></div>")
                .append("</div>");

        html.append(buildCover(data));
        html.append(buildDisclaimerBlock());
        html.append(buildSummary(data));
        // M22: chỉ các khối tuỳ chọn theo lựa chọn sections; khối bắt buộc luôn có mặt.
        if (data.includes(SECTION_TREND)) {
            html.append(buildChart(data));
        }
        if (data.includes(SECTION_SCAN_LOG)) {
            html.append(buildTable(data));
        }
        html.append(buildFlags(data));
        html.append(buildMethodology(data));

        html.append("</body></html>");
        return html.toString();
    }

    private String buildCover(ExportRenderData d) {
        return """
                <section class="cover">
                  <div class="cover-header">
                    <span class="logo">CatCheck</span>
                    <span class="doc-code">%s</span>
                  </div>
                  <h1>%s</h1>
                  <p class="subtitle">(CatCheck)</p>
                  <table class="cover-table">
                    <tr><td class="label">%s</td><td>%s</td></tr>
                    %s<tr><td class="label">%s</td><td>%s</td></tr>
                    <tr><td class="label">%s</td><td>%s – %s</td></tr>
                    <tr><td class="label">%s</td><td>%s</td></tr>
                  </table>
                </section>
                """.formatted(
                escape(t("export.pdf.doc_code", d.documentCode())), t("export.pdf.cover.title"),
                t("export.pdf.cover.cat"), escape(d.catName()), buildProfileRows(d),
                t("export.pdf.cover.owner"), escape(nullToDash(d.ownerName())),
                t("export.pdf.cover.range"), d.rangeFrom().format(dateFmt), d.rangeTo().format(dateFmt),
                t("export.pdf.cover.generated"),
                t("export.pdf.cover.generated_at", d.generatedAtLocal().format(dateTimeFmt)));
    }

    /** Hàng hồ sơ chi tiết của mèo trên bìa — chỉ khi user chọn khối PROFILE (M22). */
    private String buildProfileRows(ExportRenderData d) {
        if (!d.includes(SECTION_PROFILE)) {
            return "";
        }
        return """
                <tr><td class="label">%s</td><td>%s</td></tr>
                <tr><td class="label">%s</td><td>%s</td></tr>
                <tr><td class="label">%s</td><td>%s</td></tr>
                <tr><td class="label">%s</td><td>%s</td></tr>
                """.formatted(
                t("export.pdf.profile.breed"), escape(nullToDash(d.breedLabel())),
                t("export.pdf.profile.sex"), escape(nullToDash(d.sexLabel())),
                t("export.pdf.profile.age"), escape(nullToDash(d.ageLabel())),
                t("export.pdf.profile.weight"), escape(nullToDash(d.weightLabel())));
    }

    private String buildDisclaimerBlock() {
        return """
                <section class="disclaimer-block">
                  <div class="disclaimer-header"><span class="icon">ⓘ</span> %s</div>
                  <div class="emergency-block">
                    <div class="emergency-title">%s</div>
                    <div>%s</div>
                  </div>
                </section>
                <div style="page-break-after: always;"></div>
                """.formatted(t("export.pdf.header"), t("export.pdf.emergency.title"), t("export.pdf.emergency.body"));
    }

    private String buildSummary(ExportRenderData d) {
        String outOfRangeLine = d.nearBoundaryCount() > 0
                ? t("export.pdf.summary.out_of_range_near", d.outOfRangeCount(), d.nearBoundaryCount())
                : t("export.pdf.summary.out_of_range", d.outOfRangeCount());
        return """
                <section class="summary">
                  <h2>%s</h2>
                  <p>%s</p>
                </section>
                """.formatted(t("export.pdf.summary.title"),
                t("export.pdf.summary.body", d.validCount(),
                        java.time.temporal.ChronoUnit.DAYS.between(d.rangeFrom(), d.rangeTo()) + 1,
                        nullToDash(str(d.minPh())), nullToDash(str(d.maxPh())), nullToDash(str(d.medianPh())),
                        outOfRangeLine));
    }

    private String buildChart(ExportRenderData d) {
        return """
                <section class="chart-section">
                  <h2>%s</h2>
                  %s
                </section>
                """.formatted(t("export.pdf.chart.title"), d.svgChart());
    }

    private String buildTable(ExportRenderData d) {
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
                        row.disputed() ? " <span class=\"disputed\">" + t("export.pdf.table.disputed") + "</span>" : "",
                        escape(row.confidenceLabel()),
                        row.qualityFlagLabel() == null ? "—" : escape(row.qualityFlagLabel())))
                .collect(Collectors.joining());

        return """
                <section class="table-section">
                  <h2>%s</h2>
                  <table class="scan-table">
                    <thead><tr><th>%s</th><th>%s</th><th>%s</th><th>%s</th><th>%s</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </section>
                """.formatted(t("export.pdf.table.title"), t("export.pdf.table.date_time"), t("export.pdf.table.ph"),
                t("export.pdf.table.classification"), t("export.pdf.table.confidence"), t("export.pdf.table.quality"),
                rows);
    }

    private String buildFlags(ExportRenderData d) {
        if (d.flags().isEmpty()) {
            return "";
        }
        String items = d.flags().stream().map(f -> "<li>" + escape(f.explanationVi()) + "</li>").collect(Collectors.joining());
        return """
                <section class="flags-section">
                  <h2>%s</h2>
                  <ul>%s</ul>
                </section>
                """.formatted(t("export.pdf.flags.title"), items);
    }

    private String buildMethodology(ExportRenderData d) {
        String conditionalLine = d.anyNonCardCcm()
                ? "<p><em>" + t("export.pdf.methodology.no_card_note") + "</em></p>"
                : "";
        String chartVersion = d.chartVersionSnapshot() == null
                ? t("export.pdf.methodology.uncalibrated")
                : String.valueOf(d.chartVersionSnapshot());
        return """
                <section class="methodology">
                  <h2>%s</h2>
                  <p>%s</p>
                  %s
                  <p><strong>%s</strong></p>
                  <ul>
                    <li>%s</li>
                    <li>%s</li>
                    <li>%s</li>
                    <li>%s</li>
                    <li>%s</li>
                  </ul>
                </section>
                """.formatted(t("export.pdf.methodology.title"), t("export.pdf.methodology.intro", chartVersion),
                conditionalLine, t("export.pdf.methodology.limits_title"),
                t("export.pdf.methodology.limit1"), t("export.pdf.methodology.limit2"),
                t("export.pdf.methodology.limit3"), t("export.pdf.methodology.limit4"),
                t("export.pdf.methodology.limit5"));
    }

    private String t(String code, Object... args) {
        return messages.resolve(code, args, locale);
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
