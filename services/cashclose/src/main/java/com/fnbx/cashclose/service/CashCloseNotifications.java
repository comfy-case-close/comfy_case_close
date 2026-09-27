package com.fnbx.cashclose.service;

import com.fnbx.cashclose.entity.CashClose;
import com.fnbx.cashclose.repository.CashCloseEmailRecipientsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class CashCloseNotifications {
    private final CashCloseEmailRecipientsRepository recipients;
    private final ApplicationEventPublisher events;

    public CashCloseNotifications(CashCloseEmailRecipientsRepository recipients, ApplicationEventPublisher events) {
        this.recipients = recipients;
        this.events = events;
    }

    public void submitted(CashClose close) {
        var submitter = recipients.submitter(close.getBusinessId(), close.getSubmittedBy());
        var branchShift = recipients.branchShift(close.getBusinessId(), close.getBranchId(),
                close.getShiftTypeId());
        events.publishEvent(new CashCloseSubmittedEvent(close.getCashCloseId(), close.getBusinessId(),
                close.getBranchId(), close.getCashCloseCode(), branchShift.branchCode(), branchShift.shiftCode(),
                close.getBusinessDate(), submitter.name(), submitter.email(), close.getStatus().name(),
                recipients.managerEmails(close.getBusinessId(), close.getBranchId())));
    }
}
