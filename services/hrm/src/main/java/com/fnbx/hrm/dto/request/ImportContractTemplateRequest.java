package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.ContractTemplateKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record ImportContractTemplateRequest(UUID positionId, ContractTemplateKind kind, @NotBlank String name,
                                            @NotNull LocalDate effectiveFrom) {
}
