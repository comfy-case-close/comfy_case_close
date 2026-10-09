package com.fnbx.hrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Display name and order only - basis and scope are not editable via the API. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayComponentUpdateRequest {
    @NotBlank
    private String componentName;
    private short displayOrder;
}
