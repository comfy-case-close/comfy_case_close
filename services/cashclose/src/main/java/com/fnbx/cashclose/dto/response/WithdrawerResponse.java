package com.fnbx.cashclose.dto.response;

import java.util.UUID;

/**
 * A person a cash transfer may name as {@code withdrawnBy}: active staff with a
 * live WITHDRAWAL_RECORD grant at the branch — the same rule the service
 * enforces when a transfer is recorded or corrected.
 */
public record WithdrawerResponse(UUID staffId, String employeeCode, String firstName, String lastName) {}
