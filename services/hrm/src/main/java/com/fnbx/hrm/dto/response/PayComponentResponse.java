package com.fnbx.hrm.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayComponentResponse {
    private UUID payComponentId;
    private String componentCode;
    private String componentName;
    private String componentType;
    private String prorationBasis;
    private String shareScope;
    private String appliesTo;
    private boolean manualInput;
    private boolean allowNegative;
    private short displayOrder;
}
