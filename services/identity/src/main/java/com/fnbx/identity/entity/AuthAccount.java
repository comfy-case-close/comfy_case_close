package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;

/** Identity's private credential projection; never returned by a controller. */
public record AuthAccount(UUID staffId, UUID businessId, String employeeCode, String firstName, String lastName, String email,
                          String phone, String avatarUrl, String passwordHash, String passwordAlgorithm,
                          boolean active, boolean businessActive, boolean emailVerified, String authProvider,
                          Instant tokensValidFrom, long refreshVersion) {
    @Override public String toString() { return "AuthAccount[" + staffId + "]"; }
}
