package com.fnbx.hrm.service;

import com.fnbx.hrm.dto.request.FailPaymentRequest;
import com.fnbx.hrm.dto.request.MarkPaidRequest;
import com.fnbx.hrm.dto.request.PaymentFilter;
import com.fnbx.hrm.dto.response.BankAccountResponse;
import com.fnbx.hrm.dto.response.PaymentListResponse;
import com.fnbx.hrm.dto.response.PaymentResponse;
import java.util.List;
import java.util.UUID;

/** Manual salary transfers made by the accountant; the system only records what was paid. */
public interface PayrollPaymentService {

    PaymentListResponse list(PaymentFilter filter);

    List<PaymentResponse> markPaid(MarkPaidRequest request);

    PaymentResponse fail(UUID paymentId, FailPaymentRequest request);

    /** Returns the unmasked account and records who looked at it. */
    BankAccountResponse readBankAccount(UUID paymentId);
}
