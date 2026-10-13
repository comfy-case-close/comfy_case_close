package com.fnbx.identity.entity;

import java.time.Instant;
import java.util.UUID;

public record AuthAccount(UUID staffId, UUID businessId, String employeeCode, String firstName, String lastName, String email,
                          String phone, String avatarUrl, String passwordHash, String passwordAlgorithm,
                          boolean active, boolean businessActive, boolean emailVerified, String authProvider,
                          Instant tokensValidFrom, long refreshVersion, String nickname) {
    @Override public String toString() { return "AuthAccount[" + staffId + "]"; }
}
