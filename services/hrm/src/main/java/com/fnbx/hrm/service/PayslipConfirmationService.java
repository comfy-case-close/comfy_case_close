package com.fnbx.hrm.service;

import com.fnbx.hrm.service.confirmation.ClientInfo;
import java.util.UUID;

/** Confirmation of a received salary from inside the app, plus the HR actions around it. */
public interface PayslipConfirmationService {

    void confirmOwn(UUID payslipId, ClientInfo client);

    void resend(UUID payslipId);

    /** E-mails every payslip of the period whose employee has not confirmed yet; returns how many were reminded. */
    int remindPending(UUID periodId);
}
