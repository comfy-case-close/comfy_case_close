package com.fnbx.hrm.service.contractdoc.docx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class DocxToXhtmlConverter {

    private static final String DOCUMENT_ENTRY = "word/document.xml";
    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String NON_BREAKING_SPACE = "&#160;";
    private static final String UNALIGNED_TAB = NON_BREAKING_SPACE.repeat(4);
    private static final int HEADING_MAX_CHARACTERS = 90;
    private static final double TABLE_RIGHT_BORDER_ALLOWANCE_POINTS = 1;

    private final UnaryOperator<String> textRewriter;

    public DocxToXhtmlConverter(UnaryOperator<String> textRewriter) {
        this.textRewriter = textRewriter;
    }

    public String convert(InputStream docx) {
        Element body = WordXml.child(parse(documentXml(docx)).getDocumentElement(), "body");
        Element sectionProperties = WordXml.child(body, "sectPr");
        double contentWidthPoints = DocxStyles.contentWidthPoints(sectionProperties);
        StringBuilder html = new StringBuilder(DocxStyles.pageCss(sectionProperties)).append("<div class=\"docx\">");
        WordXml.children(body).forEach(block -> writeBlock(block, contentWidthPoints, html));
        return html.append("</div>").toString();
    }

    private byte[] documentXml(InputStream docx) {
        try (ZipInputStream zip = new ZipInputStream(docx)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals(DOCUMENT_ENTRY)) {
                    return zip.readAllBytes();
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("The file is not a Word document", ex);
        }
        throw new IllegalArgumentException("The file is not a Word document: " + DOCUMENT_ENTRY + " is missing");
    }

    private Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(DISALLOW_DOCTYPE, true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IllegalArgumentException("The Word document cannot be read", ex);
        }
    }

    private void writeBlock(Element block, double contentWidthPoints, StringBuilder html) {
        switch (block.getLocalName()) {
            case "p" -> writeParagraph(block, html);
            case "tbl" -> writeTable(block, contentWidthPoints, html);
            default -> { }
        }
    }

    private void writeParagraph(Element paragraph, StringBuilder html) {
        Element properties = WordXml.child(paragraph, "pPr");
        List<String> segments = splitIntoSegments(paragraph);
        ParagraphLayout layout = layoutOf(properties, segments);
        String style = DocxStyles.paragraphCss(properties, layout, isHeadingKeptWithNext(paragraph, properties));
        switch (layout) {
            case PLAIN -> writePlainParagraph(style, segments, html);
            case RIGHT_ALIGNED_COLUMN -> writeRightAlignedColumn(style, segments, html);
            case HANGING_NUMBER -> writeHangingNumber(style, DocxStyles.hangingPoints(properties), segments, html);
        }
    }

    private ParagraphLayout layoutOf(Element properties, List<String> segments) {
        if (properties == null || segments.size() == 1) {
            return ParagraphLayout.PLAIN;
        }
        if (hasRightAlignedTab(properties) && segments.size() == 2) {
            return ParagraphLayout.RIGHT_ALIGNED_COLUMN;
        }
        return DocxStyles.hangingPoints(properties) > 0 ? ParagraphLayout.HANGING_NUMBER : ParagraphLayout.PLAIN;
    }

    private boolean hasRightAlignedTab(Element properties) {
        Element tabs = WordXml.child(properties, "tabs");
        return tabs != null && WordXml.children(tabs).stream().anyMatch(tab -> "right".equals(WordXml.attribute(tab, "val")));
    }

    private boolean isHeadingKeptWithNext(Element paragraph, Element properties) {
        return properties != null && WordXml.has(properties, "keepNext") && textLength(paragraph) <= HEADING_MAX_CHARACTERS;
    }

    private int textLength(Element paragraph) {
        return WordXml.children(paragraph, "r").stream().mapToInt(run -> run.getTextContent().length()).sum();
    }

    private void writePlainParagraph(String style, List<String> segments, StringBuilder html) {
        String content = String.join(UNALIGNED_TAB, segments);
        html.append("<p style=\"").append(style).append("\">")
                .append(content.isEmpty() ? NON_BREAKING_SPACE : content)
                .append("</p>");
    }

    private void writeRightAlignedColumn(String style, List<String> segments, StringBuilder html) {
        html.append("<div style=\"").append(style).append("\">")
                .append("<span style=\"float: right\">").append(segments.get(1)).append("</span>")
                .append(segments.get(0))
                .append("</div>");
    }

    private void writeHangingNumber(String style, double numberColumnPoints, List<String> segments, StringBuilder html) {
        html.append("<div style=\"").append(style).append("\">")
                .append("<table style=\"width: 100%; table-layout: auto\"><tr>")
                .append("<td style=\"padding: 0; width: ").append(Css.points(numberColumnPoints)).append("; vertical-align: top\">")
                .append(segments.get(0)).append("</td>")
                .append("<td style=\"padding: 0\">").append(String.join(UNALIGNED_TAB, segments.subList(1, segments.size()))).append("</td>")
                .append("</tr></table></div>");
    }

    private List<String> splitIntoSegments(Element paragraph) {
        List<List<StyledText>> segments = new ArrayList<>();
        segments.add(new ArrayList<>());
        for (Element run : WordXml.children(paragraph, "r")) {
            String style = DocxStyles.runCss(WordXml.child(run, "rPr"));
            for (Element part : WordXml.children(run)) {
                switch (part.getLocalName()) {
                    case "t" -> appendText(segments, style, part.getTextContent());
                    case "br" -> lastSegment(segments).add(StyledText.lineBreak(style));
                    case "tab" -> segments.add(new ArrayList<>());
                    default -> { }
                }
            }
        }
        return segments.stream().map(this::toHtml).toList();
    }

    private void appendText(List<List<StyledText>> segments, String style, String text) {
        String[] pieces = text.split("\t", -1);
        for (int index = 0; index < pieces.length; index++) {
            if (index > 0) {
                segments.add(new ArrayList<>());
            }
            appendJoiningNeighbour(lastSegment(segments), style, pieces[index]);
        }
    }

    private void appendJoiningNeighbour(List<StyledText> segment, String style, String text) {
        if (segment.isEmpty() || !segment.get(segment.size() - 1).canJoin(style)) {
            segment.add(StyledText.text(style, text));
            return;
        }
        int lastIndex = segment.size() - 1;
        segment.set(lastIndex, segment.get(lastIndex).joinedWith(text));
    }

    private List<StyledText> lastSegment(List<List<StyledText>> segments) {
        return segments.get(segments.size() - 1);
    }

    private String toHtml(List<StyledText> texts) {
        StringBuilder html = new StringBuilder();
        for (StyledText text : texts) {
            String content = text.isLineBreak() ? "<br/>" : escape(textRewriter.apply(text.text()));
            if (text.style().isEmpty() || content.isEmpty()) {
                html.append(content);
            } else {
                html.append("<span style=\"").append(text.style()).append("\">").append(content).append("</span>");
            }
        }
        return html.toString();
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void writeTable(Element table, double contentWidthPoints, StringBuilder html) {
        Element properties = WordXml.child(table, "tblPr");
        TableDimensions dimensions = TableDimensions.of(properties, contentWidthPoints - TABLE_RIGHT_BORDER_ALLOWANCE_POINTS);
        html.append("<table style=\"").append(dimensions.css()).append("\">");
        List<Element> rows = WordXml.children(table, "tr");
        writeColumns(WordXml.child(table, "tblGrid"), rows, dimensions, html);
        writeRows(rows, WordXml.child(properties, "tblBorders"), contentWidthPoints, html);
        html.append("</table>");
    }

    private void writeColumns(Element grid, List<Element> rows, TableDimensions dimensions, StringBuilder html) {
        html.append("<colgroup>");
        if (grid != null) {
            List<Element> columns = WordXml.children(grid);
            for (int index = 0; index < columns.size(); index++) {
                double cellWidth = Css.twipsToPoints(WordXml.attribute(columns.get(index), "w")) * dimensions.shrink();
                double contentWidth = cellWidth - DocxStyles.horizontalPaddingPoints(firstCellOfColumn(rows, index));
                html.append("<col style=\"width: ").append(Css.points(contentWidth)).append("\"/>");
            }
        }
        html.append("</colgroup>");
    }

    private Element firstCellOfColumn(List<Element> rows, int columnIndex) {
        if (rows.isEmpty()) {
            return null;
        }
        List<Element> cells = WordXml.children(rows.get(0), "tc");
        return columnIndex < cells.size() ? WordXml.child(cells.get(columnIndex), "tcPr") : null;
    }

    private void writeRows(List<Element> rows, Element tableBorders, double contentWidthPoints, StringBuilder html) {
        boolean headerOpen = false;
        for (Element row : rows) {
            Element rowProperties = WordXml.child(row, "trPr");
            boolean header = WordXml.has(rowProperties, "tblHeader");
            if (header && !headerOpen) {
                html.append("<thead>");
            } else if (!header && headerOpen) {
                html.append("</thead>");
            }
            headerOpen = header;
            writeRow(row, WordXml.has(rowProperties, "cantSplit"), tableBorders, contentWidthPoints, html);
        }
        if (headerOpen) {
            html.append("</thead>");
        }
    }

    private void writeRow(Element row, boolean unsplittable, Element tableBorders, double contentWidthPoints, StringBuilder html) {
        html.append(unsplittable ? "<tr style=\"page-break-inside: avoid\">" : "<tr>");
        for (Element cell : WordXml.children(row, "tc")) {
            html.append("<td style=\"").append(DocxStyles.cellCss(WordXml.child(cell, "tcPr"), tableBorders)).append("\">");
            WordXml.children(cell).forEach(block -> writeBlock(block, contentWidthPoints, html));
            html.append("</td>");
        }
        html.append("</tr>");
    }

    private record StyledText(String style, String text, boolean isLineBreak) {

        static StyledText text(String style, String text) {
            return new StyledText(style, text, false);
        }

        static StyledText lineBreak(String style) {
            return new StyledText(style, "", true);
        }

        boolean canJoin(String otherStyle) {
            return !isLineBreak && style.equals(otherStyle);
        }

        StyledText joinedWith(String more) {
            return text(style, text + more);
        }
    }

    private record TableDimensions(double width, double indent, double shrink) {

        static TableDimensions of(Element tableProperties, double availableWidth) {
            Element widthElement = WordXml.child(tableProperties, "tblW");
            double width = widthElement != null && "dxa".equals(WordXml.attribute(widthElement, "type"))
                    ? Css.twipsToPoints(WordXml.attribute(widthElement, "w")) : 0;
            double indent = Css.twipsToPoints(WordXml.attribute(WordXml.child(tableProperties, "tblInd"), "w"));
            boolean overflows = width > 0 && indent + width > availableWidth;
            return new TableDimensions(width, indent, overflows ? (availableWidth - indent) / width : 1);
        }

        String css() {
            StringBuilder css = new StringBuilder(Css.declaration("table-layout", "fixed"));
            if (width > 0) {
                css.append(Css.pointsDeclaration("width", width * shrink));
            }
            if (indent > 0) {
                css.append(Css.pointsDeclaration("margin-left", indent));
            }
            return css.toString().trim();
        }
    }
}
