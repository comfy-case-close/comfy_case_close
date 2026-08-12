package com.comfy.caseclose.utils;

import java.util.Locale;

/**
 * Centralises all input normalisation rules so every service applies them
 * identically before writing to the database.
 *
 * Rules extracted from GAS Code.gs conventions:
 *  - employeeCode : trim + toUpperCase  (Code.gs normaliseEmployeeCode)
 *  - email        : trim + toLowerCase  (standard; avoids duplicate-check misses)
 *  - fullName     : trim + collapse internal whitespace
 *  - position     : trim + collapse internal whitespace
 *  - branchCode   : trim + toUpperCase
 *  - shiftTypeCode: trim + toUpperCase
 *  - freeText     : trim only (note, description, etc.)
 */
public final class InputNormalizer {

    private InputNormalizer() {}

    /** Employee code: trim + uppercase.  Null-safe → returns null for null input. */
    public static String employeeCode(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    /** Email: trim + lowercase.  Null-safe → returns null for null input. */
    public static String email(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Human name (fullName, position): trim + collapse multiple spaces to one. */
    public static String name(String value) {
        if (value == null) return null;
        String collapsed = value.strip().replaceAll("\\s{2,}", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** Branch / shift-type codes: trim + uppercase. */
    public static String code(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    /** Free text (note, description): trim only.  Returns null for blank input. */
    public static String text(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
