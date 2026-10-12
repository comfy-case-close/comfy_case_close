package com.fnbx.hrm.service.payment;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TransferNoteBuilder {

    private static final int MAX_LENGTH = 50;

    public String build(int month, int year, String employeeCode, String fullName) {
        String note = "LUONG T%02d-%d %s %s".formatted(month, year, employeeCode, fullName);
        String ascii = stripDiacritics(note).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 -]", "").trim();
        return ascii.length() <= MAX_LENGTH ? ascii : ascii.substring(0, MAX_LENGTH).trim();
    }

    private String stripDiacritics(String text) {
        String decomposed = Normalizer.normalize(text.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }
}
