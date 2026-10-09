package com.fnbx.hrm.service.contractdoc;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Component;

@Component
public class ContractPdfRenderer {

    private static final String FONT_FAMILY = "Noto Sans";
    private static final String PAGE_HEAD = """
            <html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
            <style>@page { size: A4; margin: 20mm; } body { font-family: 'Noto Sans'; font-size: 11pt; line-height: 1.5; }
            h1 { font-size: 16pt; text-align: center; } table { width: 100%; border-collapse: collapse; }</style>
            </head><body>""";

    /** {@code bodyHtml} is the already rendered template body, a well-formed XHTML fragment. */
    public byte[] render(String bodyHtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> font("NotoSans-Regular.ttf"), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> font("NotoSans-Bold.ttf"), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(PAGE_HEAD + bodyHtml + "</body></html>", null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("The contract PDF could not be rendered", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("The contract PDF could not be rendered: " + ex.getMessage(), ex);
        }
    }

    public byte[] merge(List<byte[]> documents) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            documents.forEach(document -> merger.addSource(new ByteArrayInputStream(document)));
            merger.setDestinationStream(out);
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("The contract PDFs could not be merged", ex);
        }
    }

    private InputStream font(String name) {
        return ContractPdfRenderer.class.getResourceAsStream("/fonts/" + name);
    }
}
