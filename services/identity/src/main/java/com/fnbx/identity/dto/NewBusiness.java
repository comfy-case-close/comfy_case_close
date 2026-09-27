package com.fnbx.identity.dto;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import com.fnbx.shared.enums.BusinessType;
import com.fnbx.shared.exception.AppException;
import com.fnbx.shared.exception.ErrorCode;

/** Normalized business data and server-generated codes shared by submission and approval. */
public record NewBusiness(String businessCode, String businessName, BusinessType businessType,
                          String currencyCode, String timezone,
                          String branchCode, String branchName, String branchAddress) {

    public static final BusinessType DEFAULT_TYPE = BusinessType.CAFE;
    public static final String DEFAULT_CURRENCY = "VND";
    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    public static final String DEFAULT_BRANCH_CODE = "MAIN";

    public static NewBusiness normalised(String businessCode, String businessName, BusinessType businessType,
            String currencyCode, String timezone, String branchCode, String branchName, String branchAddress) {
        return new NewBusiness(
                code(required(businessCode, "businessCode")),
                required(businessName, "businessName"),
                businessType == null ? DEFAULT_TYPE : businessType,
                blank(currencyCode) ? DEFAULT_CURRENCY : currencyCode.strip().toUpperCase(Locale.ROOT),
                zone(blank(timezone) ? DEFAULT_TIMEZONE : timezone.strip()),
                code(blank(branchCode) ? DEFAULT_BRANCH_CODE : branchCode),
                required(branchName, "branchName"),
                trimmed(branchAddress));
    }

    /** The database CHECKs that codes are uppercase; normalise rather than reject on case. */
    private static String code(String value) { return value.strip().toUpperCase(Locale.ROOT); }

    /**
     * A bad timezone is not a cosmetic error: {@code submit_deadline} is evaluated in
     * this zone, so an unrecognised one would silently change which cash closes count
     * as late. Reject it here rather than storing it.
     */
    private static String zone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (DateTimeException invalid) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "timezone must be a valid IANA zone ID");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trimmed(String value) { return blank(value) ? null : value.strip(); }
    private static String required(String value, String field) {
        if (blank(value)) throw new AppException(ErrorCode.VALIDATION_FAILED, field + " is required");
        return value.strip();
    }
}
