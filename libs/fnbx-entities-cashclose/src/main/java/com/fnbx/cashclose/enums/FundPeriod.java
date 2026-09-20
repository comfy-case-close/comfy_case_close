package com.fnbx.cashclose.enums;

/**
 * Grouping period of a fund withdrawal. Matches PostgreSQL enum
 * {@code shared.fund_period}.
 *
 * <p>Lives in the cashclose module, not in {@code fnbx-shared}: only
 * {@code FundWithdrawal} uses it. An enum in the shared library has fan-in 9/9,
 * so adding a value there would force a rebuild of every service - including ones
 * that have no idea what a fund withdrawal is.
 *
 * <p>The PostgreSQL type stays in the {@code shared} schema, because there the
 * constraint is different: types must exist before any column uses them, and
 * 001-shared runs first.
 */
public enum FundPeriod { DAILY, WEEKLY, MONTHLY, ADHOC }
