package com.fnbx.identity.utils.enums;

/**
 * Why an OTP was issued. Codes are namespaced by purpose, so a code mailed to prove ownership of a
 * new address can never be replayed against the password-reset flow (or the other way round).
 */
public enum OtpPurpose {

    /** Proving ownership of an address before a local account is created. */
    SIGNUP,

    BUSINESS_REGISTRATION,

    /** Proving ownership of the address on an existing account before its password is replaced. */
    PASSWORD_RESET
}
