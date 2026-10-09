package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.response.ConfirmationViewResponse;
import com.fnbx.hrm.entity.PayrollPayment;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.entity.PayslipConfirmation;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.BankRepository;
import com.fnbx.hrm.repository.PayrollPaymentRepository;
import com.fnbx.hrm.repository.PayrollPeriodRepository;
import com.fnbx.hrm.repository.PayslipConfirmationRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.hrm.service.PublicPayslipConfirmationService;
import com.fnbx.hrm.service.confirmation.ClientInfo;
import com.fnbx.hrm.service.confirmation.ConfirmationTokenResolver;
import com.fnbx.hrm.service.confirmation.PayslipConfirmationTokens;
import com.fnbx.hrm.service.confirmation.PayslipConfirmationWorkflow;
import com.fnbx.hrm.service.confirmation.PublicRequestLimiter;
import com.fnbx.hrm.service.employee.SensitiveMask;
import com.fnbx.shared.tenant.TenantContext;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PublicPayslipConfirmationServiceImpl implements PublicPayslipConfirmationService {

    private final PublicRequestLimiter limiter;
    private final PayslipConfirmationTokens tokens;
    private final ConfirmationTokenResolver resolver;
    private final PayslipConfirmationWorkflow workflow;
    private final PayslipConfirmationRepository confirmationRepository;
    private final PayslipRepository payslipRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final BankRepository bankRepository;
    private final TransactionTemplate transaction;

    @Override
    public ConfirmationViewResponse view(String token, ClientInfo client) {
        return inTenant(token, client, this::viewOf);
    }

    @Override
    public ConfirmationViewResponse confirm(String token, ClientInfo client) {
        return inTenant(token, client, confirmation -> {
            workflow.confirm(confirmation, client);
            return viewOf(confirmation);
        });
    }

    @Override
    public ConfirmationViewResponse dispute(String token, String note, ClientInfo client) {
        return inTenant(token, client, confirmation -> {
            workflow.dispute(confirmation, note, client);
            return viewOf(confirmation);
        });
    }

    private ConfirmationViewResponse inTenant(String token, ClientInfo client, Function<PayslipConfirmation, ConfirmationViewResponse> action) {
        String hash = tokens.hash(token);
        limiter.check(client.ip(), hash);
        UUID businessId = resolver.resolveBusiness(hash).orElseThrow(PayrollExceptions::confirmationTokenInvalid);
        return TenantContext.runAs(TenantContext.of(businessId, null), () -> transaction.execute(status -> {
            PayslipConfirmation confirmation = confirmationRepository.findByTokenHash(hash)
                    .orElseThrow(PayrollExceptions::confirmationTokenInvalid);
            return action.apply(workflow.requireUsable(confirmation));
        }));
    }

    private ConfirmationViewResponse viewOf(PayslipConfirmation confirmation) {
        Payslip payslip = payslipRepository.findById(confirmation.getPayslipId()).orElseThrow(PayrollExceptions::payslipNotFound);
        PayrollPeriod period = periodRepository.findById(payslip.getPeriodId()).orElseThrow(PayrollExceptions::periodNotFound);
        PayrollPayment payment = paymentRepository.findByPayslipId(payslip.getPayslipId()).orElse(null);
        String bankName = payment == null || payment.getBankCode() == null ? null
                : bankRepository.findById(payment.getBankCode()).map(bank -> bank.getShortName()).orElse(null);
        return new ConfirmationViewResponse(period.getPeriodMonth(), period.getPeriodYear(), payslip.getNetTotal(), bankName,
                payment == null ? null : SensitiveMask.mask(payment.getBankAccountNo()),
                confirmation.getStatus().name(), confirmation.getConfirmedAt());
    }
}
