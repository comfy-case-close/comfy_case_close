package com.fnbx.hrm.enums;

/**
 * How a monthly allowance amount is split across a person's payroll lines
 * (spec section 5.5.1). Let {@code S} be the lines in the component's
 * {@link ShareScope} and {@code share(line) = line.allowanceHours / Sum_S allowanceHours}.
 *
 * <ul>
 *   <li>{@code NONE} - not prorated; the line-local amount is used as-is
 *       ({@code BASE_PAY}, {@code OT_PAY}, {@code WEEKEND_PREMIUM}).</li>
 *   <li>{@code DAYS_CAPPED} - {@code amount = A * MIN(Sum_S allowanceWorkdays / D, 1) * share(line)}.
 *       Can never exceed 100% of the contract amount (R05). Used for
 *       {@code LUNCH_ALW} and the non-fixed branch of {@code RESP_ALW}.</li>
 *   <li>{@code FULL_SPLIT} - {@code amount = A * share(line)}, always summing to
 *       exactly {@code A}. Used for {@code HOUSING_ALW}, {@code PHONE_ALW},
 *       {@code FUEL_ALW}, and the fixed-salary branch of {@code RESP_ALW}.</li>
 * </ul>
 */
public enum ProrationBasis {
    NONE, DAYS_CAPPED, FULL_SPLIT
}
