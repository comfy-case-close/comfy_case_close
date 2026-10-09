package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputePayslipRequest(@NotBlank @Size(max = 1000) String note) {
}
