package com.comfy.caseclose.utils.enums;

public enum DiffDirection {
    // Cash is short vs POS expectation; signed_amount > 0.
    SHORTAGE,
    // Cash is over; signed_amount < 0.
    SURPLUS
}
