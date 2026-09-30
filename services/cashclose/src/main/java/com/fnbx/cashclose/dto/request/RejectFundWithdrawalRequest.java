package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotBlank;
public record RejectFundWithdrawalRequest(@NotBlank String reason) {}
