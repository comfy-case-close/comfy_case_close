package com.fnbx.hrm.dto.request;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Empty {@code payslipIds} means every not-yet-sent payslip of the period. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendPayslipsRequest {
    private List<UUID> payslipIds;
}
