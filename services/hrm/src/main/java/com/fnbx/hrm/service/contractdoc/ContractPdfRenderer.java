package com.fnbx.hrm.service.contractdoc;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Component;

@Component
public class ContractPdfRenderer {

    private static final String FALLBACK_FONT_FAMILY = "Noto Sans";
    private static final String CONTRACT_FONT_FAMILY = "Tinos";
    private static final String DEFAULT_PAGE_CSS = """
            <html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
            <style>@page { size: A4; margin: 20mm; } body { margin: 0; font-family: 'Noto Sans'; font-size: 11pt; line-height: 1.5; }
            h1 { font-size: 16pt; text-align: center; } table { width: 100%; border-collapse: collapse; }</style>
            """;
    private static final Pattern STYLE_BLOCK = Pattern.compile("<style>.*?</style>", Pattern.DOTALL);

    public byte[] render(String bodyHtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(page(bodyHtml), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
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

    private void registerFonts(PdfRendererBuilder builder) {
        builder.useFont(() -> font("NotoSans-Regular.ttf"), FALLBACK_FONT_FAMILY, 400, FontStyle.NORMAL, true);
        builder.useFont(() -> font("NotoSans-Bold.ttf"), FALLBACK_FONT_FAMILY, 700, FontStyle.NORMAL, true);
        builder.useFont(() -> font("Tinos-Regular.ttf"), CONTRACT_FONT_FAMILY, 400, FontStyle.NORMAL, true);
        builder.useFont(() -> font("Tinos-Bold.ttf"), CONTRACT_FONT_FAMILY, 700, FontStyle.NORMAL, true);
        builder.useFont(() -> font("Tinos-Italic.ttf"), CONTRACT_FONT_FAMILY, 400, FontStyle.ITALIC, true);
        builder.useFont(() -> font("Tinos-BoldItalic.ttf"), CONTRACT_FONT_FAMILY, 700, FontStyle.ITALIC, true);
    }

    private String page(String bodyHtml) {
        return DEFAULT_PAGE_CSS + styleBlocksOf(bodyHtml) + "</head><body>" + withoutStyleBlocks(bodyHtml) + "</body></html>";
    }

    private String styleBlocksOf(String html) {
        StringBuilder styles = new StringBuilder();
        Matcher blocks = STYLE_BLOCK.matcher(html);
        while (blocks.find()) {
            styles.append(blocks.group());
        }
        return styles.toString();
    }

    private String withoutStyleBlocks(String html) {
        return STYLE_BLOCK.matcher(html).replaceAll("");
    }

    private InputStream font(String name) {
        return ContractPdfRenderer.class.getResourceAsStream("/fonts/" + name);
    }
}
