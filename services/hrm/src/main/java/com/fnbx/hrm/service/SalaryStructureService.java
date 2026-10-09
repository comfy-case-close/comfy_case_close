package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.SalaryPreviewRequest;
import com.fnbx.hrm.dto.response.SalaryPreviewResponse;

public interface SalaryStructureService {

    SalaryPreviewResponse preview(SalaryPreviewRequest request);
}
