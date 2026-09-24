package com.comfy.caseclose.utils.enums;

public enum ApprovalAction {
    APPROVE,
    REJECT,
    VOID,
    REQUEST_REVISION,
    /** An admin edited an already-submitted close's numbers — see CashCloseServiceImpl#updateCashClose. */
    EDIT
}
