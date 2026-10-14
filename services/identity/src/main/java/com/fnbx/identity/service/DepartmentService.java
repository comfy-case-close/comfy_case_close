package com.fnbx.identity.service;

import java.util.List;
import java.util.UUID;
import com.fnbx.identity.dto.request.DepartmentRequest;
import com.fnbx.identity.dto.response.DepartmentResponse;

/**
 * The department catalogue of a business. identity owns the writes; hrm and the other services read the table
 * with a join (README 4.1).
 *
 * <p>Every method takes its business from the verified token. None accepts a {@code businessId}.
 */
public interface DepartmentService {

    /** Needs any of DEPARTMENT_MANAGE, HR_RECORD_READ, PAYROLL_PROCESS or PAYROLL_CONFIG_WRITE. Includes inactive ones. */
    List<DepartmentResponse> list();

    /** Needs DEPARTMENT_MANAGE. The code is stored upper case; a code already used in the business is refused. */
    DepartmentResponse create(DepartmentRequest request);

    /** Needs DEPARTMENT_MANAGE. Replaces the department's fields. */
    DepartmentResponse update(UUID departmentId, DepartmentRequest request);

    /** Needs DEPARTMENT_MANAGE. Switches the department off; repeating it is harmless. */
    void deactivate(UUID departmentId);
}
