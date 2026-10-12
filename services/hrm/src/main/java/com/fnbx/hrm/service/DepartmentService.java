package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.DepartmentRequest;
import com.fnbx.hrm.dto.response.DepartmentResponse;
import java.util.List;
import java.util.UUID;

public interface DepartmentService {

    List<DepartmentResponse> list();

    DepartmentResponse create(DepartmentRequest request);

    DepartmentResponse update(UUID departmentId, DepartmentRequest request);

    void deactivate(UUID departmentId);
}
