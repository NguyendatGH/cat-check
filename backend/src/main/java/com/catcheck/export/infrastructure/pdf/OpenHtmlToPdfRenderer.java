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
