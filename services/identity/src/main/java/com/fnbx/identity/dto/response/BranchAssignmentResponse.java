package com.fnbx.identity.dto.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One staff member at one branch, with all of their position assignments.
 *
 * <p>Each position retains its assignment dates so the roster can include revoked
 * assignments without losing their history.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchAssignmentResponse {

    private UUID staffId;
    private UUID branchId;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String email;
    private boolean staffActive;
    private List<BranchPositionAssignmentResponse> positions;
}
