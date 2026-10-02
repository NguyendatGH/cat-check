package com.catcheck.export.domain.port;

/**
 * Cổng render HTML → PDF (p13 §13.7: openhtmltopdf + Batik cho SVG inline). Tách khỏi
 * {@code application} vì thư viện render (org.apache.pdfbox, com.openhtmltopdf) là chi tiết hạ
 * tầng (R2).
 */
public interface PdfRenderer {

    Result render(String xhtml);

    record Result(byte[] bytes, int pageCount) {
    }
}
