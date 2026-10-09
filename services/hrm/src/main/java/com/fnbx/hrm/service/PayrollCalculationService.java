package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.response.PayrollLineCalculationResponse;
import com.fnbx.hrm.dto.response.PayrollLineResponse;
import com.fnbx.hrm.dto.response.PayrollRunResponse;
import java.util.List;
import java.util.UUID;

public interface PayrollCalculationService {

    /** Always returns 200; a FAILED run is a normal outcome, carried in the response body. */
    PayrollRunResponse runPayroll(UUID periodId);

    List<PayrollRunResponse> listRuns(UUID periodId);

    PayrollRunResponse getRun(UUID runId);

    List<PayrollLineResponse> getPayrollTable(UUID periodId);

    PayrollLineCalculationResponse getLineCalculation(UUID lineId);
}
