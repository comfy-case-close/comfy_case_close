package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The body of a lock is optional; the note, when given, is written to the decision history. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockPayrollPeriodRequest {
    @Size(max = 500)
    private String note;
}
