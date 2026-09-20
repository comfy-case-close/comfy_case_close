package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional note recorded against an approved registration. Not sent to the
 * applicant - the approval letter is generated from the business that was created,
 * so a reviewer's internal remark stays internal.
 */
public record ApproveRegistrationRequest(@Size(max = 500) String note) {}
