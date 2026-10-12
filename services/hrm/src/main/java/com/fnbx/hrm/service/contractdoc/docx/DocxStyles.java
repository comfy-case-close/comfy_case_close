package com.fnbx.hrm.service.contractdoc.docx;

import java.util.List;
import org.w3c.dom.Element;

final class DocxStyles {

    private static final double WORD_DEFAULT_FONT_POINTS = 13;
    private static final double LINE_SPACING_UNITS_PER_SINGLE_LINE = 240;
    private static final double FALLBACK_CONTENT_WIDTH_POINTS = 467;
    private static final String FALLBACK_PAGE_MARGINS = "20mm";
    private static final String PAGE_NUMBER_FONT = "font-size: 11pt; font-family: 'Tinos', 'Noto Sans';";
    private static final List<String> NO_BORDER_VALUES = List.of("none", "nil");
    private static final List<String> SIDES = List.of("top", "left", "bottom", "right");
    private static final double DEFAULT_CELL_SIDE_PADDING_POINTS = 5.4;
    private static final double MINIMUM_BORDER_POINTS = 0.5;
    private static final double BORDER_EIGHTHS_PER_POINT = 8;
    private static final String DEFAULT_BORDER_COLOR = "#000000";

    private DocxStyles() {
    }

    static String pageCss(Element sectionProperties) {
        return "<style>"
                + "@page { size: A4; margin: " + pageMargins(sectionProperties) + "; " + pageNumberBox() + " } "
                + ".docx { font-family: 'Tinos', 'Noto Sans'; " + Css.declaration("font-size", fontSize(WORD_DEFAULT_FONT_POINTS)).trim() + " } "
                + ".docx p { margin: 0; } "
                + ".docx table { border-collapse: collapse; } "
                + ".docx td { vertical-align: top; }"
                + "</style>";
    }

    static double contentWidthPoints(Element sectionProperties) {
        Element pageSize = WordXml.child(sectionProperties, "pgSz");
        Element pageMargins = WordXml.child(sectionProperties, "pgMar");
        if (pageSize == null || pageMargins == null) {
            return FALLBACK_CONTENT_WIDTH_POINTS;
        }
        return Css.twipsToPoints(WordXml.attribute(pageSize, "w"))
                - Css.twipsToPoints(WordXml.attribute(pageMargins, "left"))
                - Css.twipsToPoints(WordXml.attribute(pageMargins, "right"));
    }

    static String runCss(Element runProperties) {
        if (runProperties == null) {
            return "";
        }
        StringBuilder css = new StringBuilder();
        if (WordXml.isSwitchedOn(runProperties, "b")) {
            css.append(Css.declaration("font-weight", "bold"));
        }
        if (WordXml.isSwitchedOn(runProperties, "i")) {
            css.append(Css.declaration("font-style", "italic"));
        }
        if (isUnderlined(runProperties)) {
            css.append(Css.declaration("text-decoration", "underline"));
        }
        css.append(runFontSize(runProperties));
        css.append(runColor(runProperties));
        return css.toString().trim();
    }

    static String paragraphCss(Element paragraphProperties, ParagraphLayout layout, boolean keepWithNext) {
        if (paragraphProperties == null) {
            return "";
        }
        StringBuilder css = new StringBuilder();
        css.append(alignment(paragraphProperties));
        css.append(spacing(paragraphProperties));
        css.append(indent(paragraphProperties, layout));
        css.append(paragraphBorders(paragraphProperties));
        if (keepWithNext) {
            css.append(Css.declaration("page-break-after", "avoid"));
        }
        return css.toString().trim();
    }

    static double hangingPoints(Element paragraphProperties) {
        return Css.twipsToPoints(WordXml.attribute(WordXml.child(paragraphProperties, "ind"), "hanging"));
    }

    static String cellCss(Element cellProperties, Element tableBorders) {
        return (cellBorders(cellProperties, tableBorders) + cellPadding(cellProperties) + cellBackground(cellProperties)).trim();
    }

    static double horizontalPaddingPoints(Element cellProperties) {
        return cellPaddingPoints(cellProperties, "left") + cellPaddingPoints(cellProperties, "right");
    }

    private static String pageMargins(Element sectionProperties) {
        Element pageMargins = WordXml.child(sectionProperties, "pgMar");
        if (pageMargins == null) {
            return FALLBACK_PAGE_MARGINS;
        }
        return "%s %s %s %s".formatted(margin(pageMargins, "top"), margin(pageMargins, "right"),
                margin(pageMargins, "bottom"), margin(pageMargins, "left"));
    }

    private static String margin(Element pageMargins, String side) {
        return Css.points(Css.twipsToPoints(WordXml.attribute(pageMargins, side)));
    }

    private static String pageNumberBox() {
        return "@bottom-center { content: \"Trang \" counter(page) \"/\" counter(pages); " + PAGE_NUMBER_FONT + " }";
    }

    private static String fontSize(double wordPoints) {
        return Css.points(wordPoints);
    }

    private static boolean isUnderlined(Element runProperties) {
        Element underline = WordXml.child(runProperties, "u");
        return underline != null && !"none".equals(WordXml.attribute(underline, "val"));
    }

    private static String runFontSize(Element runProperties) {
        String halfPoints = WordXml.attribute(WordXml.child(runProperties, "sz"), "val");
        if (halfPoints == null) {
            return "";
        }
        double points = Double.parseDouble(halfPoints) / 2;
        return points == WORD_DEFAULT_FONT_POINTS ? "" : Css.declaration("font-size", fontSize(points));
    }

    private static String runColor(Element runProperties) {
        String color = WordXml.attribute(WordXml.child(runProperties, "color"), "val");
        return color == null || "auto".equals(color) ? "" : Css.colorDeclaration("color", color);
    }

    private static String alignment(Element paragraphProperties) {
        String alignment = WordXml.attribute(WordXml.child(paragraphProperties, "jc"), "val");
        if (alignment == null) {
            return "";
        }
        return Css.declaration("text-align", switch (alignment) {
            case "center" -> "center";
            case "right", "end" -> "right";
            case "both", "distribute" -> "justify";
            default -> "left";
        });
    }

    private static String spacing(Element paragraphProperties) {
        Element spacing = WordXml.child(paragraphProperties, "spacing");
        if (spacing == null) {
            return "";
        }
        String css = Css.twipsDeclaration("margin-top", WordXml.attribute(spacing, "before"))
                + Css.twipsDeclaration("margin-bottom", WordXml.attribute(spacing, "after"));
        String line = WordXml.attribute(spacing, "line");
        boolean proportionalLine = line != null && "auto".equals(WordXml.attribute(spacing, "lineRule"));
        return proportionalLine
                ? css + Css.declaration("line-height", Css.number(Double.parseDouble(line) / LINE_SPACING_UNITS_PER_SINGLE_LINE))
                : css;
    }

    private static String indent(Element paragraphProperties, ParagraphLayout layout) {
        Element indent = WordXml.child(paragraphProperties, "ind");
        if (indent == null) {
            return "";
        }
        double left = Css.twipsToPoints(WordXml.attribute(indent, "left"));
        double hanging = Css.twipsToPoints(WordXml.attribute(indent, "hanging"));
        boolean hangingInColumn = layout == ParagraphLayout.HANGING_NUMBER;
        String css = hangingInColumn
                ? Css.pointsDeclaration("margin-left", Math.max(0, left - hanging))
                : Css.twipsDeclaration("margin-left", WordXml.attribute(indent, "left"));
        css += Css.twipsDeclaration("margin-right", WordXml.attribute(indent, "right"));
        return hangingInColumn ? css : css + textIndent(indent, hanging);
    }

    private static String textIndent(Element indent, double hanging) {
        if (hanging > 0) {
            return Css.declaration("text-indent", "-" + Css.points(hanging));
        }
        return Css.twipsDeclaration("text-indent", WordXml.attribute(indent, "firstLine"));
    }

    private static String paragraphBorders(Element paragraphProperties) {
        Element borders = WordXml.child(paragraphProperties, "pBdr");
        if (borders == null) {
            return "";
        }
        StringBuilder css = new StringBuilder();
        for (Element side : WordXml.children(borders)) {
            css.append(borderCss("border-" + side.getLocalName(), side));
        }
        return css.toString();
    }

    private static String cellBorders(Element cellProperties, Element tableBorders) {
        Element ownBorders = WordXml.child(cellProperties, "tcBorders");
        StringBuilder css = new StringBuilder();
        for (String side : SIDES) {
            Element border = WordXml.child(ownBorders, side);
            if (border == null) {
                border = WordXml.child(tableBorders, side);
            }
            String property = "border-" + side;
            css.append(border == null ? Css.declaration(property, "none") : borderCss(property, border));
        }
        return css.toString();
    }

    private static String cellPadding(Element cellProperties) {
        StringBuilder css = new StringBuilder();
        for (String side : SIDES) {
            css.append(Css.pointsDeclaration("padding-" + side, cellPaddingPoints(cellProperties, side)));
        }
        return css.toString();
    }

    private static double cellPaddingPoints(Element cellProperties, String side) {
        String twips = WordXml.attribute(WordXml.child(WordXml.child(cellProperties, "tcMar"), side), "w");
        if (twips != null) {
            return Css.twipsToPoints(twips);
        }
        return side.equals("left") || side.equals("right") ? DEFAULT_CELL_SIDE_PADDING_POINTS : 0;
    }

    private static String cellBackground(Element cellProperties) {
        String fill = WordXml.attribute(WordXml.child(cellProperties, "shd"), "fill");
        return fill == null || "auto".equals(fill) ? "" : Css.colorDeclaration("background-color", fill);
    }

    private static String borderCss(String property, Element border) {
        String value = WordXml.attribute(border, "val");
        if (value == null || NO_BORDER_VALUES.contains(value)) {
            return Css.declaration(property, "none");
        }
        return Css.declaration(property, Css.points(borderWidth(border)) + " solid " + borderColor(border));
    }

    private static double borderWidth(Element border) {
        String eighths = WordXml.attribute(border, "sz");
        return eighths == null ? MINIMUM_BORDER_POINTS : Math.max(MINIMUM_BORDER_POINTS, Double.parseDouble(eighths) / BORDER_EIGHTHS_PER_POINT);
    }

    private static String borderColor(Element border) {
        String color = WordXml.attribute(border, "color");
        return color == null || "auto".equals(color) ? DEFAULT_BORDER_COLOR : "#" + color;
    }
}
