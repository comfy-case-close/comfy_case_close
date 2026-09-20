package com.fnbx.shared.utils;

import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

import java.time.LocalDate;

public final class ValidationUtils {

    public static void requireValidDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.DATE_RANGE_INVALID);
        }
    }
}
