package com.fnbx.identity.dto.request;

import jakarta.validation.constraints.*;

/**
 * Refuses an application. The reason is required, and the database agrees: a CHECK
 * on {@code staff_join_request} refuses a REJECTED row with a blank note. A silent
 * rejection makes a later dispute unresolvable - the same rule the cash-close
 * ledger applies to a rejected movement.
 */
public record RejectJoinRequest(@NotBlank @Size(max = 500) String reason) {}
