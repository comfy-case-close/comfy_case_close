package com.fnbx.identity.service;

import java.util.UUID;

/** Contains a generated credential on approval; keep only in memory and never log its body. */
public record RegistrationEmailRequested(UUID registrationId, String email, String subject, String body) {
    @Override public String toString() { return "RegistrationEmailRequested[id=" + registrationId + "]"; }
}
