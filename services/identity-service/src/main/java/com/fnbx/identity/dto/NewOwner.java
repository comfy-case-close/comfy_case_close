package com.fnbx.identity.dto;

import java.util.Locale;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

/** Normalized owner details. Approval separately supplies the generated password after email verification. */
public record NewOwner(String email, String firstName, String lastName, String phone) {

    public static NewOwner normalised(String email, String firstName, String lastName, String phone) {
        return new NewOwner(
                required(email, "email").toLowerCase(Locale.ROOT),
                required(firstName, "firstName"),
                required(lastName, "lastName"),
                blank(phone) ? null : phone.strip());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String required(String value, String field) {
        if (blank(value)) throw new AppException(ErrorCode.VALIDATION_FAILED, field + " is required");
        return value.strip();
    }
}
