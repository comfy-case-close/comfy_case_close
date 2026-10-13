package com.fnbx.hrm.service.contractdoc;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ContractFormats {

    static final Locale VIETNAM = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ContractFormats() {
    }

    static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    static String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(VIETNAM).format(amount) + " đồng";
    }

    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
