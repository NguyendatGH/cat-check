package com.catcheck.export.infrastructure.pdf;

import com.catcheck.export.domain.port.PdfRenderer;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Hiện thực {@link PdfRenderer} bằng {@code openhtmltopdf-pdfbox} + {@code openhtmltopdf-svg-support}
 * (Batik) — đúng phương án đã chốt ở p13 §13.7 (server tự vẽ SVG string, không dùng thư viện chart).
 *
 * <p><b>Font (xem docs/handovers/A6.md):</b> p13 §13.5 chọn Be Vietnam Pro, nhưng file font đó
 * KHÔNG có sẵn trong môi trường build này (không có quyền tải tệp ngoài). Dùng tạm
 * {@code DejaVu Sans} (bundled sẵn trong {@code src/main/resources/fonts/}, giấy phép Bitstream
 * Vera — được phép nhúng lại), phủ đủ dấu tiếng Việt (Latin Extended Additional). Đổi sang Be
 * Vietnam Pro thật trước go-live chỉ cần thay 2 file .ttf, không đổi logic.</p>
 */
@Component
public class OpenHtmlToPdfRenderer implements PdfRenderer {

    private static final String FONT_FAMILY = "DejaVu Sans";

    @Override
    public Result render(String xhtml) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withProducer("CatCheck");
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans.ttf"), FONT_FAMILY,
                    400, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.useFont(() -> getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf"), FONT_FAMILY,
                    700, BaseRendererBuilder.FontStyle.NORMAL, true);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(output);
            builder.run();

            byte[] bytes = output.toByteArray();
            int pageCount = countPages(bytes);
            return new Result(bytes, pageCount);
        } catch (IOException ex) {
            throw new IllegalStateException("Không render được PDF: " + ex.getMessage(), ex);
        }
    }

    private int countPages(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return document.getNumberOfPages();
        } catch (IOException ex) {
            return 0;
        }
    }
}
