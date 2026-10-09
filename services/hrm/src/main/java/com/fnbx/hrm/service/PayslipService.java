package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.SendPayslipsRequest;
import com.fnbx.hrm.dto.response.PayslipDetailResponse;
import com.fnbx.hrm.dto.response.PayslipEmailLogResponse;
import com.fnbx.hrm.dto.response.PayslipResponse;
import java.util.List;
import java.util.UUID;

public interface PayslipService {

    /** Creates or overwrites the period's payslips. Requires non-stale payroll results. */
    List<PayslipResponse> generate(UUID periodId);

    List<PayslipResponse> list(UUID periodId);

    PayslipDetailResponse get(UUID payslipId);

    void send(UUID periodId, SendPayslipsRequest request);

    void sendOne(UUID payslipId);

    List<PayslipEmailLogResponse> listEmailLogs(UUID payslipId);
}
