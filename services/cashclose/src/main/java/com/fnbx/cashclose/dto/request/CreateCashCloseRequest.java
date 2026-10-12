package com.fnbx.cashclose.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Opens a shift record. Branch is supplied by X-Branch-Id. */
@Data
public class CreateCashCloseRequest {
    @NotNull
    private UUID shiftTypeId;
    @NotNull
    private LocalDate businessDate;
}
