package com.fnbx.cashclose.enums;

/**
 * Risk level of a cash close. Matches PostgreSQL enum {@code shared.risk_level}.
 *
 * <p><b>Never stored on a table.</b> It is a function of the unexplained
 * difference and the two thresholds snapshotted at submit time. Because those
 * thresholds are frozen per close, the value stays stable even after the owner
 * changes the config - which a stored column could not guarantee.
 *
 * <p>Computed in two places that MUST agree: {@code cashclose.v_cash_close_overview}
 * for dashboards and ad-hoc SQL, and {@code CashCloseView.deriveRisk} for the API.
 * Change one without the other and the same close reports two different risk levels.
 *
 * <p>Distinct from {@code notify.AlertSeverity}: this grades a close, that grades
 * a notification.
 */
public enum RiskLevel { LOW, MEDIUM, HIGH }
