package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FailPaymentRequest(@NotBlank String reason) {
}
