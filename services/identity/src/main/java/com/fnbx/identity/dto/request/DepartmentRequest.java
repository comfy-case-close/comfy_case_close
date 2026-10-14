package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates or replaces a department of the caller's business.
 *
 * <p>No {@code businessId}: it comes from the verified JWT. The code is stored upper case and trimmed
 * ({@code identity.department} rejects anything else); {@code displayOrder} defaults to 0 and
 * {@code active} to true when absent.
 */
public record DepartmentRequest(
        @NotBlank @Size(max = 64) String departmentCode,
        @NotBlank @Size(max = 200) String departmentName,
        @Min(0) @Max(32767) Integer displayOrder,
        Boolean active) {}
