package com.fnbx.hrm.service.employee;

public final class SensitiveMask {

    private static final int VISIBLE_TAIL = 4;

    private SensitiveMask() {}

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String compact = value.replaceAll("\\s+", "");
        String tail = compact.length() <= VISIBLE_TAIL ? compact : compact.substring(compact.length() - VISIBLE_TAIL);
        return "•••• " + tail;
    }
}
