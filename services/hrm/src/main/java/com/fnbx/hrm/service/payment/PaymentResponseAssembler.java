package com.fnbx.hrm.service.payment;

import com.fnbx.hrm.dto.response.PaymentResponse;
import com.fnbx.hrm.entity.Bank;
import com.fnbx.hrm.entity.PayrollPayment;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.repository.BankRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.hrm.service.employee.SensitiveMask;
import com.fnbx.identity.entity.Staff;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentResponseAssembler {

    private final PayslipRepository payslipRepository;
    private final BankRepository bankRepository;
    private final EntityManager entityManager;

    public List<PaymentResponse> assemble(List<PayrollPayment> payments) {
        Map<UUID, Payslip> payslips = payslipRepository
                .findAllById(payments.stream().map(PayrollPayment::getPayslipId).toList()).stream()
                .collect(Collectors.toMap(Payslip::getPayslipId, Function.identity()));
        Map<String, Bank> banks = bankRepository.findAll().stream()
                .collect(Collectors.toMap(Bank::getBankCode, Function.identity()));
        return payments.stream().map(payment -> toResponse(payment, payslips.get(payment.getPayslipId()), banks)).toList();
    }

    public static boolean isMissingBankInfo(PayrollPayment payment) {
        return payment.getBankCode() == null || payment.getBankAccountNo() == null || payment.getBankAccountNo().isBlank();
    }

    private PaymentResponse toResponse(PayrollPayment payment, Payslip payslip, Map<String, Bank> banks) {
        Staff staff = entityManager.find(Staff.class, payslip.getStaffId());
        Bank bank = payment.getBankCode() == null ? null : banks.get(payment.getBankCode());
        return new PaymentResponse(payment.getPayrollPaymentId(), payment.getPayslipId(), payslip.getStaffId(),
                staff.getEmployeeCode(), staff.getFirstName() + " " + staff.getLastName(), payment.getAmount(),
                payment.getBankCode(), bank == null ? null : bank.getShortName(),
                SensitiveMask.mask(payment.getBankAccountNo()), payment.getBankAccountName(),
                payment.getTransferNote(), payment.getStatus().name(), payment.getPaidAt(), payment.getBankReference(),
                payment.getFailureReason(), isMissingBankInfo(payment));
    }
}
