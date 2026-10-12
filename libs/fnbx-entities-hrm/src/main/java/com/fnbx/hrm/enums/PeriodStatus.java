package com.fnbx.hrm.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@code DRAFT -> LOCKED -> PAID}, plus {@code LOCKED -> DRAFT} (unlock, spec
 * section 8.4 E42 - requires a reason, always audited). {@code PAID} is
 * terminal: once money has gone out, the period does not reopen.
 *
 * <p>Any write to timesheet, roster or manual items outside {@code DRAFT} is
 * rejected with {@code PERIOD_LOCKED} (spec section 11).
 */
public enum PeriodStatus {
    DRAFT, LOCKED, PAID;

    public Set<PeriodStatus> allowedNext() {
        return switch (this) {
            case DRAFT  -> EnumSet.of(LOCKED);
            case LOCKED -> EnumSet.of(DRAFT, PAID);
            case PAID   -> EnumSet.noneOf(PeriodStatus.class);
        };
    }

    public boolean canTransitionTo(PeriodStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}
