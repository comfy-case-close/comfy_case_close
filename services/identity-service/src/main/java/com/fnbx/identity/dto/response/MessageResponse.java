package com.fnbx.identity.dto.response;

/** A simple human-readable result for actions that return no resource (e.g. password reset). */
public record MessageResponse(String message) {}
