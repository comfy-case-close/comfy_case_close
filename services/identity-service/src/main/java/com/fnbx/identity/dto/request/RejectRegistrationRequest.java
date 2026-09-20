package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.*;

/**
 * Refuses a registration. The reason is required, and the database agrees - a
 * CHECK on {@code business_registration} refuses a REJECTED row with a blank note.
 *
 * <p>Unlike an approval note, <b>this text is emailed to the applicant verbatim</b>.
 * Write it as something a stranger should read: "the business licence number does
 * not match the registered name", not "looks dodgy". It is the only explanation
 * they will get.
 */
public record RejectRegistrationRequest(@NotBlank @Size(max = 500) String reason) {}
