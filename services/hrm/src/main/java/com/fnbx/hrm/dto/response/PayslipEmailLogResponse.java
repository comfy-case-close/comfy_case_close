package com.fnbx.hrm.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayslipEmailLogResponse {
    private UUID payslipEmailLogId;
    private String recipientEmail;
    private String subject;
    private String status;
    private Instant sentAt;
    private String errorMessage;
}
