package com.comfy.caseclose.utils;

import com.comfy.caseclose.exception.BadRequestException;

import java.time.LocalDate;
import java.time.ZoneId;

public final class BusinessDates {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BusinessDates() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static void requireNotInFuture(LocalDate date, String fieldName) {
        if (date == null) {
            return;
        }
        LocalDate today = today();
        if (date.isAfter(today)) {
            throw new BadRequestException(
                    fieldName + " (" + date + ") cannot be in the future (today is " + today + ")");
        }
    }
}
