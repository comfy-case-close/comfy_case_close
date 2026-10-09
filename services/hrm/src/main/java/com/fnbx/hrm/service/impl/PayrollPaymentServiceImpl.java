package com.fnbx.hrm.service.impl;

import com.fnbx.hrm.dto.request.FailPaymentRequest;
import com.fnbx.hrm.dto.request.MarkPaidRequest;
import com.fnbx.hrm.dto.request.PaymentFilter;
import com.fnbx.hrm.dto.response.BankAccountResponse;
import com.fnbx.hrm.dto.response.PaymentListResponse;
import com.fnbx.hrm.dto.response.PaymentResponse;
import com.fnbx.hrm.entity.Bank;
import com.fnbx.hrm.entity.EmploymentAssignment;
import com.fnbx.hrm.entity.PayrollLine;
import com.fnbx.hrm.entity.PayrollPayment;
import com.fnbx.hrm.entity.PayrollPaymentEvent;
import com.fnbx.hrm.enums.PaymentAction;
import com.fnbx.hrm.enums.PaymentStatus;
import com.fnbx.hrm.exception.PayrollExceptions;
import com.fnbx.hrm.repository.BankRepository;
import com.fnbx.hrm.repository.EmploymentAssignmentRepository;
import com.fnbx.hrm.repository.PayrollLineRepository;
import com.fnbx.hrm.repository.PayrollPaymentEventRepository;
import com.fnbx.hrm.repository.PayrollPaymentRepository;
import com.fnbx.hrm.service.PayrollPaymentService;
import com.fnbx.hrm.service.payment.PaymentResponseAssembler;
import com.fnbx.shared.security.BranchAccessGuard;
import com.fnbx.shared.security.Permission;
import com.fnbx.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollPaymentServiceImpl implements PayrollPaymentService {

    private final PayrollPaymentRepository paymentRepository;
    private final PayrollPaymentEventRepository eventRepository;
    private final PayrollLineRepository lineRepository;
    private final EmploymentAssignmentRepository assignmentRepository;
    private final BankRepository bankRepository;
    private final PaymentResponseAssembler assembler;
    private final BranchAccessGuard branchAccess;

    @Override
    @Transactional(readOnly = true)
    public PaymentListResponse list(PaymentFilter filter) {
        branchAccess.requireBusiness(Permission.PAYROLL_PAY);
        if (filter.periodId() == null) {
            throw PayrollExceptions.invalidField("periodId is required");
        }
        List<PayrollPayment> payments = paymentRepository.findByPeriodId(filter.periodId());
        Set<UUID> branchStaff = filter.branchId() == null ? null : staffAtBranch(filter.periodId(), filter.branchId());
        List<PaymentResponse> items = assembler.assemble(payments).stream()
                .filter(item -> matches(item, filter, branchStaff))
                .toList();
        return summarize(items, payments);
    }

    @Override
    @Transactional
    public List<PaymentResponse> markPaid(MarkPaidRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_PAY);
        Instant paidAt = request.paidAt() == null ? Instant.now() : request.paidAt();
        List<PayrollPayment> payments = request.paymentIds().stream().map(this::requirePayment).toList();
        payments.stream().filter(payment -> payment.getStatus() != PaymentStatus.PAID)
                .forEach(payment -> markOnePaid(payment, paidAt, request.bankReference()));
        return assembler.assemble(payments);
    }

    @Override
    @Transactional
    public PaymentResponse fail(UUID paymentId, FailPaymentRequest request) {
        branchAccess.requireBusiness(Permission.PAYROLL_PAY);
        PayrollPayment payment = requirePayment(paymentId);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(request.reason());
        payment.setPaidAt(null);
        payment.setPaidBy(null);
        payment.setBankReference(null);
        paymentRepository.saveAndFlush(payment);
        recordEvent(payment, PaymentAction.FAIL, null, null, request.reason());
        return assembler.assemble(List.of(payment)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccountResponse readBankAccount(UUID paymentId) {
        branchAccess.requireBusiness(Permission.PAYROLL_PAY);
        PayrollPayment payment = requirePayment(paymentId);
        log.info("Bank account read: payment={} reader={}", paymentId, TenantContext.current().userId());
        String bankName = payment.getBankCode() == null ? null
                : bankRepository.findById(payment.getBankCode()).map(Bank::getShortName).orElse(null);
        return new BankAccountResponse(payment.getBankCode(), bankName, payment.getBankAccountNo(), payment.getBankAccountName());
    }

    private void markOnePaid(PayrollPayment payment, Instant paidAt, String bankReference) {
        if (PaymentResponseAssembler.isMissingBankInfo(payment)) {
            throw PayrollExceptions.paymentBankInfoMissing();
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setPaidBy(TenantContext.current().userId());
        payment.setBankReference(bankReference);
        payment.setFailureReason(null);
        paymentRepository.saveAndFlush(payment);
        recordEvent(payment, PaymentAction.MARK_PAID, paidAt, bankReference, null);
    }

    private void recordEvent(PayrollPayment payment, PaymentAction action, Instant paidAt, String bankReference, String reason) {
        PayrollPaymentEvent event = new PayrollPaymentEvent();
        event.setPayrollPaymentEventId(UUID.randomUUID());
        event.setBusinessId(payment.getBusinessId());
        event.setPayrollPaymentId(payment.getPayrollPaymentId());
        event.setAction(action);
        event.setActedBy(TenantContext.current().userId());
        event.setActedAt(Instant.now());
        event.setPaidAt(paidAt);
        event.setBankReference(bankReference);
        event.setReason(reason);
        eventRepository.save(event);
    }

    private Set<UUID> staffAtBranch(UUID periodId, UUID branchId) {
        List<UUID> assignmentIds = lineRepository.findByPeriodId(periodId).stream()
                .filter(line -> branchId.equals(line.getBranchId()))
                .map(PayrollLine::getAssignmentId).toList();
        return assignmentRepository.findAllById(assignmentIds).stream()
                .map(EmploymentAssignment::getStaffId).collect(Collectors.toSet());
    }

    private boolean matches(PaymentResponse item, PaymentFilter filter, Set<UUID> branchStaff) {
        return (filter.status() == null || item.status().equals(filter.status().name()))
                && (filter.bankCode() == null || filter.bankCode().equals(item.bankCode()))
                && (branchStaff == null || branchStaff.contains(item.staffId()))
                && matchesSearch(item, filter.search());
    }

    private boolean matchesSearch(PaymentResponse item, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return item.employeeCode().toLowerCase(Locale.ROOT).contains(needle)
                || item.employeeName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private PaymentListResponse summarize(List<PaymentResponse> items, List<PayrollPayment> allPayments) {
        BigDecimal total = sum(items, null);
        BigDecimal paid = sum(items, PaymentStatus.PAID.name());
        long missing = items.stream().filter(PaymentResponse::missingBankInfo).count();
        boolean ready = !allPayments.isEmpty()
                && allPayments.stream().allMatch(payment -> payment.getStatus() == PaymentStatus.PAID);
        return new PaymentListResponse(items, total, paid, total.subtract(paid), missing, ready);
    }

    private BigDecimal sum(List<PaymentResponse> items, String status) {
        return items.stream()
                .filter(item -> status == null || status.equals(item.status()))
                .map(PaymentResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PayrollPayment requirePayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(PayrollExceptions::paymentNotFound);
    }
}
