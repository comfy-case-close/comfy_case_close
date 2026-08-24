package com.comfy.caseclose.utils.enums;

public enum MovementType {
    // Paid from the cash drawer; affects the reconciliation diff.
    EXPENSE,
    // Paid outside the shift (e.g. staff parking); does NOT affect the diff.
    END_OF_DAY_EXPENSE
}
