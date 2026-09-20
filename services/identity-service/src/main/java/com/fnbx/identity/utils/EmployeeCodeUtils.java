package com.fnbx.identity.utils;

import java.util.Locale;

public final class EmployeeCodeUtils {
    private EmployeeCodeUtils() {}

    /** Ho + Viet Bach -> bachho. Whitespace is removed from the code; accents are preserved. */
    public static String base(String firstName, String lastName) {
        String first = firstName.strip().replaceAll("(?U)\\s+", "");
        String last = lastName.strip().replaceAll("(?U)\\s+", " ");
        String finalWord = last.substring(last.lastIndexOf(' ') + 1);
        String code = (finalWord + first).toLowerCase(Locale.ROOT);
        if (code.isBlank()) throw new IllegalArgumentException("Staff name must not be blank");
        return code;
    }
}
