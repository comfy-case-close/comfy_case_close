package com.fnbx.hrm.service.contractdoc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

final class VietnameseNumberWords {

    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] SCALES = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"};
    private static final Locale VIETNAM = Locale.forLanguageTag("vi-VN");

    private VietnameseNumberWords() {
    }

    static String money(BigDecimal amount) {
        String words = of(amount.setScale(0, RoundingMode.HALF_UP).longValueExact());
        return words.substring(0, 1).toUpperCase(VIETNAM) + words.substring(1) + " đồng";
    }

    static String countWithWords(long count) {
        return count + " (" + of(count) + ")";
    }

    static String of(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("A negative amount cannot be read aloud: " + number);
        }
        if (number == 0) {
            return DIGITS[0];
        }
        int[] groups = new int[SCALES.length];
        long rest = number;
        for (int index = 0; index < groups.length; index++) {
            groups[index] = (int) (rest % 1000);
            rest /= 1000;
        }
        if (rest > 0) {
            throw new IllegalArgumentException("The amount is too large to read aloud: " + number);
        }
        StringBuilder words = new StringBuilder();
        boolean higherGroupWritten = false;
        for (int index = groups.length - 1; index >= 0; index--) {
            if (groups[index] == 0) {
                continue;
            }
            if (!words.isEmpty()) {
                words.append(' ');
            }
            words.append(group(groups[index], higherGroupWritten));
            if (!SCALES[index].isEmpty()) {
                words.append(' ').append(SCALES[index]);
            }
            higherGroupWritten = true;
        }
        return words.toString();
    }

    private static String group(int value, boolean higherGroupWritten) {
        int hundreds = value / 100;
        int tens = value / 10 % 10;
        int units = value % 10;
        StringBuilder words = new StringBuilder();
        if (hundreds > 0) {
            words.append(DIGITS[hundreds]).append(" trăm");
        } else if (higherGroupWritten) {
            words.append("không trăm");
        }
        if (tens > 1) {
            append(words, DIGITS[tens] + " mươi");
        } else if (tens == 1) {
            append(words, "mười");
        } else if (units > 0 && (hundreds > 0 || higherGroupWritten)) {
            append(words, "lẻ");
        }
        if (units > 0) {
            append(words, unit(units, tens));
        }
        return words.toString();
    }

    private static String unit(int units, int tens) {
        if (units == 1 && tens > 1) {
            return "mốt";
        }
        if (units == 5 && tens > 0) {
            return "lăm";
        }
        return DIGITS[units];
    }

    private static void append(StringBuilder words, String part) {
        if (!words.isEmpty()) {
            words.append(' ');
        }
        words.append(part);
    }
}
