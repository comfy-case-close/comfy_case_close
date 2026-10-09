package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.EmployeeAllowanceRequest;
import com.fnbx.hrm.dto.response.EmployeeAllowanceResponse;
import java.util.List;
import java.util.UUID;

public interface EmployeeAllowanceService {

    List<EmployeeAllowanceResponse> listByEmployee(UUID staffId);

    /** Allowances belong to the person, never to one assignment (R12). */
    EmployeeAllowanceResponse create(UUID staffId, EmployeeAllowanceRequest request);
}
