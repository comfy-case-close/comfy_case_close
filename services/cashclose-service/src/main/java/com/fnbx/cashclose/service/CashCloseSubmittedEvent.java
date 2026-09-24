package com.fnbx.cashclose.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot captured within the submission transaction. No entities or thread-local context cross threads. */
public record CashCloseSubmittedEvent(
        UUID cashCloseId, UUID businessId, UUID branchId, String referenceCode, String branchCode,
        String shiftTypeCode, LocalDate businessDate, String submittedBy, String submittedByEmail,
        String status, List<String> managerEmails) {
    public CashCloseSubmittedEvent { managerEmails = List.copyOf(managerEmails); }
}
