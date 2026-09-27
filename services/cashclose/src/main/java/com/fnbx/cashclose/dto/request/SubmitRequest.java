package com.fnbx.cashclose.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Submits a close. The note is the submitter's own, not a reviewer comment. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitRequest {
    private String note;
}
