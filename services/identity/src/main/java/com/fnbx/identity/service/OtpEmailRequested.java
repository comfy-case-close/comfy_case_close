package com.fnbx.identity.service;

import com.fnbx.identity.utils.enums.OtpPurpose;

/** In-memory delivery event only; never persist or log the verification code. */
public record OtpEmailRequested(OtpPurpose purpose, String scope, String email, String otp) {
    @Override public String toString() { return "OtpEmailRequested[purpose=" + purpose + "]"; }
}
