package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePayrollLineRequest {
    @NotNull
    private UUID assignmentId;
    @NotNull
    private UUID branchId;
}
