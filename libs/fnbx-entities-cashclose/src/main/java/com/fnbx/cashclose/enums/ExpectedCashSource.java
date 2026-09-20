package com.fnbx.cashclose.enums;

/**
 * Where the expected cash figure came from. Matches
 * {@code shared.expected_cash_source}.
 *
 * <p>{@code MANUAL} is a control weakness, not a neutral option: the person
 * counting the drawer also supplies the yardstick it is measured against.
 * The UI must show the two cases differently, and MANUAL closes are counted
 * separately in analytics.
 */
public enum ExpectedCashSource { POS_SYNC, MANUAL }
