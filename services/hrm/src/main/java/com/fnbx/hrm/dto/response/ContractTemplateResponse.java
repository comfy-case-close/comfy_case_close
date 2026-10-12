package com.fnbx.hrm.dto.response;

import com.fnbx.hrm.enums.ContractTemplateKind;
import java.time.LocalDate;
import java.util.UUID;

public record ContractTemplateResponse(UUID contractTemplateId, UUID positionId, String name, int version,
                                       boolean active, LocalDate effectiveFrom, String bodyHtml,
                                       ContractTemplateKind kind) {
}
