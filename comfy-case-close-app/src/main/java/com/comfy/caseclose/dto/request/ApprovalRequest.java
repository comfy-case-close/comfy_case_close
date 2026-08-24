package com.comfy.caseclose.dto.request;

import lombok.Data;

@Data
public class ApprovalRequest {

    // The action (approve / reject / void) is determined by the endpoint, not the body.
    // Optional for approve and void; required for reject (enforced in the service).
    private String note;
}
