package com.fnbx.hrm.service.payment;

import com.fnbx.hrm.entity.EmployeeProfile;
import com.fnbx.hrm.entity.PayrollPayment;
import com.fnbx.hrm.entity.PayrollPeriod;
import com.fnbx.hrm.entity.Payslip;
import com.fnbx.hrm.enums.PaymentStatus;
import com.fnbx.hrm.repository.EmployeeProfileRepository;
import com.fnbx.hrm.repository.PayrollPaymentRepository;
import com.fnbx.hrm.repository.PayslipRepository;
import com.fnbx.identity.entity.Staff;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSynchronizer {

    private final PayslipRepository payslipRepository;
    private final PayrollPaymentRepository paymentRepository;
    private final EmployeeProfileRepository profileRepository;
    private final TransferNoteBuilder transferNoteBuilder;
    private final EntityManager entityManager;

    public void sync(PayrollPeriod period) {
        payslipRepository.findByPeriodId(period.getPayrollPeriodId()).forEach(payslip -> syncOne(period, payslip));
    }

    private void syncOne(PayrollPeriod period, Payslip payslip) {
        PayrollPayment payment = paymentRepository.findByPayslipId(payslip.getPayslipId())
                .orElseGet(() -> newPayment(payslip));
        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }
        Staff staff = entityManager.find(Staff.class, payslip.getStaffId());
        EmployeeProfile profile = profileRepository.findById(payslip.getStaffId()).orElse(null);
        String fullName = staff.getFirstName() + " " + staff.getLastName();
        payment.setAmount(payslip.getNetTotal());
        payment.setBankCode(profile == null ? null : profile.getBankCode());
        payment.setBankAccountNo(profile == null ? null : profile.getBankAccountNo());
        payment.setBankAccountName(profile == null || profile.getBankAccountName() == null ? fullName : profile.getBankAccountName());
        payment.setTransferNote(transferNoteBuilder.build(
                period.getPeriodMonth(), period.getPeriodYear(), staff.getEmployeeCode(), fullName));
        paymentRepository.save(payment);
    }

    private PayrollPayment newPayment(Payslip payslip) {
        PayrollPayment payment = new PayrollPayment();
        payment.setPayrollPaymentId(UUID.randomUUID());
        payment.setBusinessId(payslip.getBusinessId());
        payment.setPayslipId(payslip.getPayslipId());
        payment.setStatus(PaymentStatus.PENDING);
        return payment;
    }
}
