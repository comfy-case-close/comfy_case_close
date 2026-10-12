package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.PayrollConfigRequest;
import com.fnbx.hrm.dto.response.PayrollConfigResponse;
import java.util.List;

public interface PayrollConfigService {

    List<PayrollConfigResponse> list();

    PayrollConfigResponse getCurrent();

    /** Creates a new version; a payroll period freezes the version in force when it was created. */
    PayrollConfigResponse create(PayrollConfigRequest request);
}
