package com.fnbx.hrm.service.contractdoc.docx;

import java.util.Locale;

final class Css {

    private static final double TWIPS_PER_POINT = 20;

    private Css() {
    }

    static double twipsToPoints(String twips) {
        return twips == null ? 0 : Double.parseDouble(twips) / TWIPS_PER_POINT;
    }

    static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    static String points(double value) {
        return number(value) + "pt";
    }

    static String declaration(String property, String value) {
        return property + ": " + value + "; ";
    }

    static String pointsDeclaration(String property, double points) {
        return declaration(property, points(points));
    }

    static String twipsDeclaration(String property, String twips) {
        return twips == null ? "" : pointsDeclaration(property, twipsToPoints(twips));
    }

    static String colorDeclaration(String property, String hex) {
        return declaration(property, "#" + hex);
    }
}
