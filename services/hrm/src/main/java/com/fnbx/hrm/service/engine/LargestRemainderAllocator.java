package com.fnbx.hrm.service.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a whole-dong total across shares so they sum EXACTLY back to it
 * (R11): each share is floored, and the leftover dong go one at a time to the
 * shares with the largest fractional remainder, ties broken by list order
 * (callers sort by {@code payroll_line.id} ascending first).
 */
public final class LargestRemainderAllocator {

    private LargestRemainderAllocator() {}

    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int n = weights.size();
        List<BigDecimal> floors = new ArrayList<>(n);
        List<BigDecimal> remainders = new ArrayList<>(n);

        if (weightSum.signum() == 0) {
            BigDecimal each = total.divide(BigDecimal.valueOf(Math.max(n, 1)), 0, RoundingMode.DOWN);
            for (int i = 0; i < n; i++) {
                floors.add(i == 0 ? total.subtract(each.multiply(BigDecimal.valueOf(n - 1L))) : each);
                remainders.add(BigDecimal.ZERO);
            }
            return floors;
        }

        for (BigDecimal weight : weights) {
            BigDecimal exact = total.multiply(weight).divide(weightSum, 6, RoundingMode.HALF_UP);
            BigDecimal floor = exact.setScale(0, RoundingMode.DOWN);
            floors.add(floor);
            remainders.add(exact.subtract(floor));
        }

        BigDecimal allocated = floors.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int remainingDong = total.subtract(allocated).intValue();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort((a, b) -> remainders.get(b).compareTo(remainders.get(a)));

        for (int i = 0; i < remainingDong && i < order.size(); i++) {
            int index = order.get(i);
            floors.set(index, floors.get(index).add(BigDecimal.ONE));
        }
        return floors;
    }
}
