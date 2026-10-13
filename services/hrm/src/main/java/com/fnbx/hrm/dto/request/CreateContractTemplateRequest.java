package com.fnbx.hrm.dto.request;

import com.fnbx.hrm.enums.ContractTemplateKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateContractTemplateRequest(UUID positionId, @NotBlank String name, @NotBlank String bodyHtml,
                                            @NotNull LocalDate effectiveFrom, ContractTemplateKind kind) {
}
