package com.fnbx.cashclose.enums;

/** Events retained for one cash movement. REOPEN exists only for legacy history. */
public enum MovementAction {
    EDIT, APPROVE, REJECT,
    @Deprecated REOPEN
}
