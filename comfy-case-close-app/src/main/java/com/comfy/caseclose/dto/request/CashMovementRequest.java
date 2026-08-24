package com.comfy.caseclose.dto.request;

import com.comfy.caseclose.utils.enums.DiffReasonType;
import com.comfy.caseclose.utils.enums.MovementCategory;
import com.comfy.caseclose.utils.enums.MovementType;
import com.comfy.caseclose.validation.ValidEnum;
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
    @ValidEnum(enumClass = MovementCategory.class)
    private String category;

    @NotBlank(message = "Type is required")
    @ValidEnum(enumClass = MovementType.class)
    private String type;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;  // VND

    @NotBlank(message = "Reason is required")
    @ValidEnum(enumClass = DiffReasonType.class)
    private String reason;  // DiffReasonType enum value

    private String description;
}
