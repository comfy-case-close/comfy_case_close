package com.fnbx.cashclose.enums;

/**
 * Lifecycle of a fund withdrawal. Matches PostgreSQL enum
 * {@code shared.fund_status}.
 *
 * <p>{@code VOIDED} matters more than it looks: the {@code EXCLUDE USING gist}
 * constraint on {@code fund_withdrawal} only ignores voided rows, so voiding is
 * how you redo a period that was drawn wrongly.
 */
public enum FundStatus { OPEN, CLOSED, VOIDED }
