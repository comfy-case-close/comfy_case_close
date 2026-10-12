package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** A template without {@code positionId} is the general one; a new request for the same position becomes its next version. */
public record CreateContractTemplateRequest(UUID positionId, @NotBlank String name, @NotBlank String bodyHtml,
                                            @NotNull LocalDate effectiveFrom) {
}
