package com.fnbx.hrm.service.report;

import java.util.Set;

public enum PayBucket {
    BEFORE_ALLOWANCE,
    ALLOWANCE,
    OTHER;

    private static final Set<String> BEFORE_ALLOWANCE_CODES =
            Set.of("BASE_PAY", "SALARY_SUPPLEMENT", "OT_PAY", "WEEKEND_PREMIUM");
    private static final Set<String> ALLOWANCE_CODES =
            Set.of("KPI_ALW", "RESP_ALW", "LUNCH_ALW", "HOUSING_ALW", "PHONE_ALW", "FUEL_ALW", "BIRTHDAY_ALLOWANCE");

    public static PayBucket of(String componentCode) {
        if (BEFORE_ALLOWANCE_CODES.contains(componentCode)) {
            return BEFORE_ALLOWANCE;
        }
        return ALLOWANCE_CODES.contains(componentCode) ? ALLOWANCE : OTHER;
    }
}
