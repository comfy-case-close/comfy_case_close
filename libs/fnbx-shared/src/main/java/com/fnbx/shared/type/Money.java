package com.fnbx.shared.type;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tien te. LUON dung BigDecimal, khong bao gio double.
 *
 * <p>Khop voi domain {@code shared.d_money} = NUMERIC(14,2).
 * VND khong co phan le, nhung giu 2 chu so thap phan de mo rong sang
 * loai tien khac ma khong phai migrate.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {}

    public static BigDecimal of(long amount) {
        return BigDecimal.valueOf(amount).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal v) {
        return v == null ? null : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** So sanh gia tri, bo qua khac biet ve scale (2.0 == 2.00). */
    public static boolean eq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }
}
