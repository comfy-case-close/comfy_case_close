package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.EmploymentAssignmentRequest;
import com.fnbx.hrm.dto.request.EndEmploymentAssignmentRequest;
import com.fnbx.hrm.dto.response.EmploymentAssignmentResponse;
import java.util.List;
import java.util.UUID;

public interface EmploymentAssignmentService {

    List<EmploymentAssignmentResponse> listByEmployee(UUID staffId);

    EmploymentAssignmentResponse create(UUID staffId, EmploymentAssignmentRequest request);

    /** Refused once any non-draft payroll period has a line against this assignment - end it and create a new one. */
    EmploymentAssignmentResponse update(UUID assignmentId, EmploymentAssignmentRequest request);

    EmploymentAssignmentResponse end(UUID assignmentId, EndEmploymentAssignmentRequest request);
}
