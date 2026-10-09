package com.fnbx.hrm.dto.response;

/** One bank an employee can be paid through. */
public record BankResponse(String bankCode, String bankName, String shortName) {
}
