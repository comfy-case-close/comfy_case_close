package com.comfy.caseclose.service;

import java.time.LocalDate;

/** Immutable submission snapshot: no lazy entities or request security context cross threads. */
public record CashCloseSubmittedEvent(
        Long cashCloseId, Long branchId, String referenceCode, String branchCode,
        String shiftTypeCode, LocalDate businessDate, String submittedBy, String submittedByEmail,
        String status) {}
