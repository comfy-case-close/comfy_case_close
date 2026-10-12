package com.fnbx.hrm.service.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LargestRemainderAllocatorTest {

    @Test
    void singleShareKeepsTheWholeTotal() {
        List<BigDecimal> shares = LargestRemainderAllocator.allocate(new BigDecimal("5000000"), List.of(new BigDecimal("40.00")));

        assertEquals(List.of(new BigDecimal("5000000")), shares);
    }

    @Test
    void sharesAlwaysAddUpToTheTotal() {
        List<BigDecimal> shares = LargestRemainderAllocator.allocate(new BigDecimal("100"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertEquals(new BigDecimal("100"), shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
