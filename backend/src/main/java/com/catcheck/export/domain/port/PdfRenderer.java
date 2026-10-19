package com.catcheck.export.domain.port;

public interface PdfRenderer {

    Result render(String xhtml);

    record Result(byte[] bytes, int pageCount) {
    }
}
