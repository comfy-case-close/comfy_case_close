package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.entity.PayslipConfirmation;
import com.fnbx.hrm.enums.ConfirmationStatus;
import com.fnbx.hrm.enums.PeriodStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.PayslipConfirmationRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.hrm.service.PayslipConfirmationService;
import com.fnbx.hrm.service.confirmation.ClientInfo;
import com.fnbx.hrm.service.confirmation.PayslipConfirmationWorkflow;
import com.fnbx.hrm.service.confirmation.PayslipMailer;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PayslipConfirmationServiceImpl implements PayslipConfirmationService {

    private final PayslipRepository payslipRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayslipConfirmationRepository confirmationRepository;
    private final PayslipConfirmationWorkflow workflow;
    private final PayslipMailer mailer;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional
    public void confirmOwn(UUID payslipId, ClientInfo client) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .filter(found -> found.getStaffId().equals(TenantContext.current().userId()))
                .orElseThrow(PayrollExceptions::payslipNotFound);
        requirePaid(payslip.getPeriodId());
        workflow.issue(payslip);
        PayslipConfirmation confirmation = confirmationRepository.findByPayslipId(payslipId).orElseThrow();
        workflow.confirm(confirmation, client);
    }

    @Override
    @Transactional
    public void resend(UUID payslipId) {
        branchAccess.requireBusiness(Permission.PAYSLIP_ISSUE);
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(PayrollExceptions::payslipNotFound);
        requirePaid(payslip.getPeriodId());
        mailer.deliver(payslip, workflow.issue(payslip));
    }

    @Override
    @Transactional
    public int remindPending(UUID periodId) {
        branchAccess.requireBusiness(Permission.PAYSLIP_ISSUE);
        requirePaid(periodId);
        List<PayslipConfirmation> pending = confirmationRepository.findByPeriodIdAndStatus(periodId, ConfirmationStatus.PENDING);
        pending.forEach(this::remind);
        return pending.size();
    }

    private void remind(PayslipConfirmation confirmation) {
        Payslip payslip = payslipRepository.findById(confirmation.getPayslipId()).orElseThrow(PayrollExceptions::payslipNotFound);
        mailer.deliver(payslip, workflow.issue(payslip));
        workflow.markReminded(confirmation);
    }

    private PayrollPeriod requirePaid(UUID periodId) {
        PayrollPeriod period = periodRepository.findById(periodId).orElseThrow(PayrollExceptions::periodNotFound);
        if (period.getStatus() != PeriodStatus.PAID) {
            throw PayrollExceptions.payslipPeriodNotPaid();
        }
        return period;
    }
}
