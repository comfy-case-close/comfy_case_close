package com.comfy.caseclose.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashMovementRequest {

    @NotBlank(message = "Category is required")
    private String category;  // MovementCategory enum value

    @NotBlank(message = "Type is required")
    private String type;  // MovementType enum value

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;  // VND

    @NotBlank(message = "Reason is required")
    private String reason;  // DiffReasonType enum value

    private String description;
}
