package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.CreatePayrollPeriodRequest;
import com.fnbx.hrm.dto.request.UnlockPayrollPeriodRequest;
import com.fnbx.hrm.dto.request.UpdatePeriodDatesRequest;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.dto.response.PayrollPeriodDecisionResponse;
import com.fnbx.hrm.dto.response.PayrollPeriodResponse;
import java.util.List;
import java.util.UUID;

public interface PayrollPeriodService {

    List<PayrollPeriodResponse> listPayrollPeriods(Short year, PeriodStatus status);

    PayrollPeriodResponse createPayrollPeriod(CreatePayrollPeriodRequest request);

    PayrollPeriodResponse getPayrollPeriod(UUID periodId);

    /** Draft periods only; the new range may not overlap another period. */
    PayrollPeriodResponse updatePeriodDates(UUID periodId, UpdatePeriodDatesRequest request);

    /** Requires: latest run SUCCEEDED, not stale, no ERROR issue, every WARNING acknowledged. */
    PayrollPeriodResponse lockPeriod(UUID periodId);

    PayrollPeriodResponse unlockPeriod(UUID periodId, UnlockPayrollPeriodRequest request);

    /** LOCKED to PAID. Irreversible. */
    PayrollPeriodResponse markPayrollPeriodPaid(UUID periodId);

    /** Lock, unlock and mark-paid history of the period, oldest first. */
    List<PayrollPeriodDecisionResponse> listDecisions(UUID periodId);
}
